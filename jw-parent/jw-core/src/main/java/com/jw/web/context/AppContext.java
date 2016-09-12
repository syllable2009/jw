package com.jw.web.context;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.jw.aop.JwProxyFactory;
import com.jw.domain.annotation.Autowired;
import com.jw.domain.annotation.Value;
import com.jw.util.ConfigUtils;
import com.jw.util.JwUtils;
import com.jw.util.PkgUtils;
import com.jw.util.StringUtils;
import com.jw.web.bind.annotation.Component;

public class AppContext {
    private static final Map<String, String> clazeMap = new ConcurrentHashMap<String, String>();

    private static final Map<String, Object> beanMap = new ConcurrentHashMap<String, Object>();

    /**
     * 是否开启了AOP
     */
    private static final boolean IS_AOP_ENABLE = ConfigUtils.getBoolean("aop.enable", false);

    static {
        Set<Class<?>> clazes = null;

        try {
            clazes = PkgUtils.findClazesByAnnotation(ConfigUtils.getProperty("package.scan"), Component.class);
            for (Class<?> claze : clazes) {
                clazeMap.put(StringUtils.lowerFirst(claze.getSimpleName()), claze.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void init() {

    }

    private AppContext() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T getBean(String beanId) {
        if (beanMap.containsKey(beanId))
            return (T) beanMap.get(beanId);

        String clazeName = clazeMap.get(beanId);
        Class<T> claze = null;

        try {
            claze = (Class<T>) Class.forName(clazeName);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        return getBean(claze);

    }

    @SuppressWarnings("unchecked")
    public static <T> T getBean(Class<T> claze) {
        String clazeName = StringUtils.lowerFirst(claze.getSimpleName());
        if (beanMap.containsKey(clazeName))
            return (T) beanMap.get(clazeName);

        try {
            T entity = autowireClaze(claze);
            beanMap.put(clazeName, entity);
            return entity;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static <T> T autowireClaze(Class<T> claze) throws Exception {
        T entity = IS_AOP_ENABLE ? JwProxyFactory.getProxyInstance(claze) : claze.newInstance();
        if (JwUtils.isAnnotated(claze, Component.class)) {
            Field[] fields = claze.getDeclaredFields();

            for (Field field : fields) {
                autowireField(entity, field);
            }
        }
        return entity;
    }

    private static <T> Object autowireField(T entity, Field field) throws Exception {
        if (JwUtils.isAnnotated(field, Autowired.class)) {
            Object value = AppContext.getBean(StringUtils.lowerFirst(field.getType().getSimpleName()));
            if (value == null && field.getAnnotation(Autowired.class).required())
                throw new Exception(String.format("Field %s in %s is not found the autowired bean.", field.getName(),
                        entity.getClass().getName()));
            field.setAccessible(true);
            field.set(entity, value);
            return value;
        }

        Value ann = field.getAnnotation(Value.class);
        if (ann == null)
            return null;
        String key = ann.value();
        if (StringUtils.isEmpty(key))
            return key;

        String value = key;
        if (JwUtils.isBindKey(key)) {
            value = ConfigUtils.getProperty(JwUtils.getBindKey(key));
        }
        Object targetValue = JwUtils.convert(field.getType(), value);
        if (targetValue != null) {
            field.setAccessible(true);
            field.set(entity, targetValue);
            return targetValue;
        }
        throw new Exception(String.format("Field %s in %s is not support autowired.", field.getName(),
                entity.getClass().getName()));
    }

}
