package com.wgg.cache.twocache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RedisConfig {

    @Value("$topic.name")
    private String topicName;

    //自定义缓存管理器放入Spring容器
    @Bean
    public TwoLevelCacheManager cacheManager(StringRedisTemplate redisTemplate) {

        RedisCacheWriter writer = RedisCacheWriter.lockingRedisCacheWriter(redisTemplate.getConnectionFactory());
        RedisSerializationContext.SerializationPair pair = RedisSerializationContext.SerializationPair.fromSerializer(new JdkSerializationRedisSerializer(this.getClass().getClassLoader()));
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig().serializeValuesWith(pair);
        //设置所有的超时时间
        config = config.entryTtl(Duration.ofSeconds(5));

        //设置单个缓存的超时时间
//        Map<String, RedisCacheConfiguration> initialCacheConfigurations = new HashMap<>(4);
//        initialCacheConfigurations.put("user", config.entryTtl(Duration.ofSeconds(60)));

        return new TwoLevelCacheManager(redisTemplate, writer, config);
    }

    //监听容器
    @Bean
    RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory, MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new PatternTopic(topicName));
        return container;
    }

    //监听事件
    @Bean
    MessageListenerAdapter listenerAdapter(final TwoLevelCacheManager cacheManager) {
        return new MessageListenerAdapter(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                byte[] bs = message.getChannel();

                try {
                    // Sub 一个消息，通知缓存管理器
                    String type = new String(bs, "UTF-8");
                    String cacheName = new String(message.getBody(), "UTF-8");
                    cacheManager.receiver(cacheName);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    // 不可能出错，忽略
                }
            }
        });
    }

    //两层管理器的实现类
    class TwoLevelCacheManager extends RedisCacheManager {
        RedisTemplate redisTemplate;

        public TwoLevelCacheManager(RedisTemplate redisTemplate, RedisCacheWriter redisCacheWriter, RedisCacheConfiguration redisCacheConfiguration){
            super(redisCacheWriter,redisCacheConfiguration);
            this.redisTemplate=redisTemplate;
        }

        private TwoLevelCacheManager(RedisTemplate redisTemplate,RedisCacheWriter redisCacheWriter,RedisCacheConfiguration redisCacheConfiguration,Map map){
            super(redisCacheWriter,redisCacheConfiguration,map);
            this.redisTemplate=redisTemplate;

        }

        //原先的RedisManager定义的是直接返回Redis中的缓存，现在从自定义的缓存里面取，主要逻辑在RedisAndLocalCahe里面
        @Override
        protected Cache decorateCache(Cache cache) {
            System.out.println("我进入自定义缓存管理器了。。。。。。。。。。。。。。");
            return new RedisAndLocalCache(this,(RedisCache)cache);
        }

        //根据频道发送消息
        public void publishMessage(String cacheName){
            System.out.println("自定义发消息啦。。。。");
            this.redisTemplate.convertAndSend(topicName,cacheName);
        }

        //根据频道接受消息
        public void receiver(String name){
            System.out.println("自定义接受消息啦。。。。。。。。");
            RedisAndLocalCache cache=(RedisAndLocalCache) this.getCache(name);
            if (cache != null){
                cache.clearLocal();
            }
        }
    }

    /**
     * @author life
     * @decription 此类只要是两层缓存的封装和实现，缓存主要思路是先从物理缓存里面取，没有从redis缓存取，主要逻辑在此类
     */
    class RedisAndLocalCache implements Cache {

        //自定义的一个本地的缓存
        ConcurrentHashMap<Object,Object> local=new ConcurrentHashMap<Object, Object>();
        //redis的缓存
        RedisCache redisCache;
        //自定义的两层缓存的管理器
        TwoLevelCacheManager twoLevelCacheManager;

        public RedisAndLocalCache(TwoLevelCacheManager twoLevelCacheManager,RedisCache redisCache){
            this.redisCache=redisCache;
            this.twoLevelCacheManager=twoLevelCacheManager;
        }

        //获取缓存名
        @Override
        public String getName() {
            System.out.println("先从本地找找。。。。。");

            System.out.println("我从redis获取缓存名字啦。。。。。。");
            return redisCache.getName();
        }

        //获取原先的缓存
        @Override
        public Object getNativeCache() {
            System.out.println("我从本地缓存获取缓存名字啦。。。。。。");
            return redisCache.getNativeCache();
        }

        //ValueWrapper是封装缓存的类，根据本地的数据类型封装
        @Override
        public ValueWrapper get(Object o) {
            System.out.println("我现在开始判断从哪获取缓存啦。。。。。");
            //先从本地获得缓存
            ValueWrapper wrapper=(ValueWrapper) local.get(o);
            if (wrapper != null){
                System.out.println("本地缓存存在缓存啦。。。。。。。");
                return wrapper;
            }else {
                //从redis获取缓存
                System.out.println("本地缓存没有我从reidis上获取啦。。。。。。。");
                wrapper=redisCache.get(o);
            }

            return wrapper;
        }

        @Override
        public <T> T get(Object o, Class<T> aClass) {
            return redisCache.get(o,aClass);
        }

        @Override
        public <T> T get(Object o, Callable<T> callable) {
            return redisCache.get(o,callable);
        }

        @Override
        public void put(Object o, Object o1) {
            System.out.println("开始往redis放缓存啦。。。。");
            redisCache.put(o,o1);
            System.out.println("开始往本地放内存。。。。。。");
            local.put(o,o1);
            System.out.println("我从频道发送消息啦。。。。。");
            clearOntherJVM();
        }

        @Override
        public ValueWrapper putIfAbsent(Object o, Object o1) {
            System.out.println("缺失的情况开始放。。。。。。");
            ValueWrapper valueWrapper=redisCache.putIfAbsent(o,o1);
            clearOntherJVM();
            return valueWrapper;
        }

        @Override
        public void evict(Object o) {
            System.out.println("删除缓存开始啦。。。。。先redis开始");
            redisCache.evict(o);
            System.out.println("删除本地开始啦。。。。");
            local.clear();
        }


        @Override
        public void clear() {
            redisCache.clear();
        }

        //通知其他的redis服务器去更新缓存
        protected void clearOntherJVM(){
            twoLevelCacheManager.publishMessage(redisCache.getName());
        }

        //清理本地的缓存
        public void clearLocal(){
            this.local.clear();
        }
    }

}






