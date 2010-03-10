/*
 * Copyright (c) 2009 Mysema Ltd.
 * All rights reserved.
 * 
 */
package com.mysema.query.codegen;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.ClassUtils;

import com.mysema.util.ReflectionUtils;

/**
 * TypeFactory is a factory class for Type instances
 * 
 * @author tiwe
 * 
 */
public final class TypeFactory {

    private final Map<List<java.lang.reflect.Type>, Type> cache = new HashMap<List<java.lang.reflect.Type>, Type>();
    
    private final Collection<Class<? extends Annotation>> entityAnnotations;

    @SuppressWarnings("unchecked")
    public TypeFactory(Class<?>... entityAnnotations){
        this.entityAnnotations = (List)Arrays.asList(entityAnnotations);
    }
    
    public TypeFactory(List<Class<? extends Annotation>> entityAnnotations){
        this.entityAnnotations = entityAnnotations;
    }
    
    public Type create(Class<?> cl){
        return create(cl, cl);
    }
    
    public Type create(Class<?> cl, java.lang.reflect.Type genericType) {
        List<java.lang.reflect.Type> key = Arrays.<java.lang.reflect.Type> asList(cl, genericType);
        if (cache.containsKey(key)) {
            return cache.get(key);
        }else{
            if (cl.isPrimitive()) {
                cl = ClassUtils.primitiveToWrapper(cl);
            }
            Type value;
            boolean entity= false;
            for (Class<? extends Annotation> clazz : entityAnnotations){
                if (cl.getAnnotation(clazz) != null){
                    entity = true;
                    break;
                }
            }            
            if (entity){
                value = new ClassType(TypeCategory.ENTITY, cl);
                
            }else if (cl.isArray()) {
                value = create(cl.getComponentType()).asArrayType();

            } else if (cl.isEnum()) {
                value = new ClassType(TypeCategory.SIMPLE, cl);

            } else if (Map.class.isAssignableFrom(cl)) {
                Type keyInfo = create(ReflectionUtils.getTypeParameter(genericType, 0));
                Type valueInfo = create(ReflectionUtils.getTypeParameter(genericType, 1));
                value = createMapType(keyInfo, valueInfo);

            } else if (List.class.isAssignableFrom(cl)) {
                Type valueInfo = create(ReflectionUtils.getTypeParameter(genericType, 0));
                value = createListType(valueInfo);

            } else if (Set.class.isAssignableFrom(cl)) {
                Type valueInfo = create(ReflectionUtils.getTypeParameter(genericType, 0));
                value = createSetType(valueInfo);
                
            } else if (Collection.class.isAssignableFrom(cl)) {
                Type valueInfo = create(ReflectionUtils.getTypeParameter(genericType, 0));
                value = createCollectionType(valueInfo);
                
            }else if (Number.class.isAssignableFrom(cl) && Comparable.class.isAssignableFrom(cl)){    
                value = new ClassType(TypeCategory.NUMERIC, cl);
                
            } else {    
                TypeCategory typeCategory = TypeCategory.get(cl.getName());
                if (!typeCategory.isSubCategoryOf(TypeCategory.COMPARABLE) && Comparable.class.isAssignableFrom(cl)){
                    typeCategory = TypeCategory.COMPARABLE;
                }
                value = new ClassType(typeCategory, cl);
            }
            cache.put(key, value);
            return value;
        }
        
    }
    
    public Type createCollectionType(Type valueType) {
        return createComposite(TypeCategory.COLLECTION, Collection.class, valueType);
    }

    private Type createComposite(TypeCategory container, Class<?> containerType, Type... parameters) {
        return new SimpleType(container, 
                containerType.getName(), 
                containerType.getPackage().getName(), 
                containerType.getSimpleName(), 
                Modifier.isFinal(containerType.getModifiers()),
                parameters);

    }
    
    public Type createListType(Type valueType) {
        return createComposite(TypeCategory.LIST, List.class, valueType);
    }

    public Type createMapType(Type keyType, Type valueType) {
        return createComposite(TypeCategory.MAP, Map.class, keyType, valueType);
    }

    public Type createSetType(Type valueType) {
        return createComposite(TypeCategory.SET, Collection.class, valueType);
    }

}