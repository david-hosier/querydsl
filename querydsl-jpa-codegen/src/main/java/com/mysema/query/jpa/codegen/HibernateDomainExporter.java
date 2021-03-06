/*
 * Copyright 2011, Mysema Ltd
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mysema.query.jpa.codegen;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.xml.stream.XMLStreamException;

import org.hibernate.cfg.Configuration;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.MappedSuperclass;
import org.hibernate.mapping.PersistentClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mysema.codegen.CodeWriter;
import com.mysema.codegen.JavaWriter;
import com.mysema.codegen.model.ClassType;
import com.mysema.codegen.model.Type;
import com.mysema.codegen.model.TypeCategory;
import com.mysema.query.QueryException;
import com.mysema.query.annotations.PropertyType;
import com.mysema.query.annotations.QueryInit;
import com.mysema.query.annotations.QueryType;
import com.mysema.query.codegen.CodegenModule;
import com.mysema.query.codegen.EmbeddableSerializer;
import com.mysema.query.codegen.EntitySerializer;
import com.mysema.query.codegen.EntityType;
import com.mysema.query.codegen.Property;
import com.mysema.query.codegen.QueryTypeFactory;
import com.mysema.query.codegen.Serializer;
import com.mysema.query.codegen.SerializerConfig;
import com.mysema.query.codegen.SimpleSerializerConfig;
import com.mysema.query.codegen.Supertype;
import com.mysema.query.codegen.SupertypeSerializer;
import com.mysema.query.codegen.TypeFactory;
import com.mysema.query.codegen.TypeMappings;
import com.mysema.query.jpa.hibernate.Constants;
import com.mysema.util.BeanUtils;

/**
 * @author tiwe
 *
 */
// TODO : fix encoding issues
public class HibernateDomainExporter {

    private static final Logger logger = LoggerFactory.getLogger(HibernateDomainExporter.class);

    private final File targetFolder;

    private final Map<String,EntityType> allTypes = new HashMap<String,EntityType>();

    private final Map<String,EntityType> entityTypes = new HashMap<String,EntityType>();

    private final Map<String,EntityType> embeddableTypes = new HashMap<String,EntityType>();

    private final Map<String,EntityType> superTypes = new HashMap<String,EntityType>();

    private final Set<EntityType> serialized = new HashSet<EntityType>();

    private final TypeFactory typeFactory = new TypeFactory(Entity.class, 
            javax.persistence.MappedSuperclass.class, Embeddable.class);

    private final Configuration configuration;

    private final QueryTypeFactory queryTypeFactory;

    private final TypeMappings typeMappings;

    private final Serializer embeddableSerializer;

    private final Serializer entitySerializer;

    private final Serializer supertypeSerializer;

    private final SerializerConfig serializerConfig;

    public HibernateDomainExporter(File targetFolder, Configuration configuration) {
        this("Q", "", targetFolder, SimpleSerializerConfig.DEFAULT, configuration);
    }

    public HibernateDomainExporter(String namePrefix, File targetFolder, Configuration configuration) {
        this(namePrefix, "", targetFolder, SimpleSerializerConfig.DEFAULT, configuration);
    }

    public HibernateDomainExporter(String namePrefix, String nameSuffix, File targetFolder,
            Configuration configuration) {
        this(namePrefix, nameSuffix, targetFolder, SimpleSerializerConfig.DEFAULT, configuration);
    }

    public HibernateDomainExporter(String namePrefix, File targetFolder, 
            SerializerConfig serializerConfig, Configuration configuration) {
        this(namePrefix, "", targetFolder, serializerConfig, configuration);
    }

    public HibernateDomainExporter(String namePrefix, String nameSuffix, File targetFolder, 
            SerializerConfig serializerConfig, Configuration configuration) {
        this.targetFolder = targetFolder;
        this.serializerConfig = serializerConfig;
        this.configuration = configuration;
        configuration.buildMappings();
        CodegenModule module = new CodegenModule();
        module.bind(CodegenModule.PREFIX, namePrefix);
        module.bind(CodegenModule.SUFFIX, nameSuffix);
        module.bind(CodegenModule.KEYWORDS, Constants.keywords);
        this.queryTypeFactory = module.get(QueryTypeFactory.class);
        this.typeMappings = module.get(TypeMappings.class);
        this.embeddableSerializer = module.get(EmbeddableSerializer.class);
        this.entitySerializer = module.get(EntitySerializer.class);
        this.supertypeSerializer = module.get(SupertypeSerializer.class);
        typeFactory.setUnknownAsEntity(true);
    }

    public void execute() throws IOException {
        // collect types
        try {
            collectTypes();
        } catch (SecurityException e) {
            throw new QueryException(e);
        } catch (XMLStreamException e) {
            throw new QueryException(e);
        } catch (ClassNotFoundException e) {
            throw new QueryException(e);
        } catch (NoSuchMethodException e) {
            throw new QueryException(e);
        }

        // merge supertype fields into subtypes
        Set<EntityType> handled = new HashSet<EntityType>();
        for (EntityType type : superTypes.values()) {
            addSupertypeFields(type, allTypes, handled);
        }
        for (EntityType type : entityTypes.values()) {
            addSupertypeFields(type, allTypes, handled);
        }
        for (EntityType type : embeddableTypes.values()) {
            addSupertypeFields(type, allTypes, handled);
        }

        // serialize them
        serialize(superTypes, supertypeSerializer);
        serialize(embeddableTypes, embeddableSerializer);
        serialize(entityTypes, entitySerializer);
    }

    private void addSupertypeFields(EntityType model, Map<String, EntityType> superTypes, 
            Set<EntityType> handled) {
        if (handled.add(model)) {
            for (Supertype supertype : model.getSuperTypes()) {
                EntityType entityType = superTypes.get(supertype.getType().getFullName());
                if (entityType != null) {
                    addSupertypeFields(entityType, superTypes, handled);
                    supertype.setEntityType(entityType);
                    model.include(supertype);
                }
            }
        }
    }


    private void collectTypes() throws IOException, XMLStreamException, ClassNotFoundException, 
        SecurityException, NoSuchMethodException {
        // super classes
        Iterator<?> superClassMappings = configuration.getMappedSuperclassMappings();
        while (superClassMappings.hasNext()) {
            MappedSuperclass msc = (MappedSuperclass)superClassMappings.next();
            EntityType entityType = createSuperType(msc.getMappedClass());
            if (msc.getDeclaredIdentifierProperty() != null) {
                handleProperty(entityType, msc.getMappedClass(), msc.getDeclaredIdentifierProperty());
            }
            Iterator<?> properties = msc.getDeclaredPropertyIterator();
            while (properties.hasNext()) {
                handleProperty(entityType, msc.getMappedClass(), (org.hibernate.mapping.Property) properties.next());
            }
        }

        // entity classes
        Iterator<?> classMappings = configuration.getClassMappings();
        while (classMappings.hasNext()) {
            PersistentClass pc = (PersistentClass)classMappings.next();
            EntityType entityType = createEntityType(pc.getMappedClass());
            if (pc.getDeclaredIdentifierProperty() != null) {
                handleProperty(entityType, pc.getMappedClass(), pc.getDeclaredIdentifierProperty());
            }else if (!pc.isInherited() && pc.hasIdentifierProperty()) {
                logger.info(entityType.toString() + pc.getIdentifierProperty());
                handleProperty(entityType, pc.getMappedClass(), pc.getIdentifierProperty());
            }
            Iterator<?> properties = pc.getDeclaredPropertyIterator();
            while (properties.hasNext()) {
                handleProperty(entityType, pc.getMappedClass(), (org.hibernate.mapping.Property) properties.next());
            }
        }
    }

    private void handleProperty(EntityType entityType, Class<?> cl, org.hibernate.mapping.Property p) 
            throws NoSuchMethodException, ClassNotFoundException {
        Type propertyType = getType(cl, p.getName());
        if (p.isComposite()) {
            Class<?> embeddedClass = Class.forName(propertyType.getFullName());
            EntityType embeddedType = createEmbeddableType(embeddedClass);
            Iterator<?> properties = ((Component)p.getValue()).getPropertyIterator();
            while (properties.hasNext()) {
                handleProperty(embeddedType, embeddedClass, (org.hibernate.mapping.Property)properties.next());
            }
            propertyType = embeddedType;
        }else if (propertyType.getCategory() == TypeCategory.ENTITY) {
            propertyType = createEntityType(Class.forName(propertyType.getFullName()));
        }
        AnnotatedElement annotated = getAnnotatedElement(cl, p.getName());
        Property property = createProperty(entityType, p.getName(), propertyType, annotated);
        entityType.addProperty(property);
    }

    @Nullable
    private Property createProperty(EntityType entityType, String propertyName, Type propertyType, 
            AnnotatedElement annotated) {
        String[] inits = new String[0];
        if (annotated.isAnnotationPresent(QueryInit.class)) {
            inits = annotated.getAnnotation(QueryInit.class).value();
        }
        if (annotated.isAnnotationPresent(QueryType.class)) {
            QueryType queryType = annotated.getAnnotation(QueryType.class);
            if (queryType.value().equals(PropertyType.NONE)) {
                return null;
            }
            propertyType = propertyType.as(TypeCategory.valueOf(queryType.value().name()));
        }
        return new Property(entityType, propertyName, propertyType, inits);
    }

    private EntityType createEntityType(Class<?> cl) {
        return createEntityType(cl, entityTypes);
    }

    private EntityType createEmbeddableType(Class<?> cl) {
        return createEntityType(cl, embeddableTypes);
    }

    private EntityType createEntityType(Class<?> cl,  Map<String,EntityType> types) {
        if (types.containsKey(cl.getName())) {
            return types.get(cl.getName());
        } else {
            EntityType type = new EntityType(new ClassType(TypeCategory.ENTITY, cl));
            typeMappings.register(type, queryTypeFactory.create(type));
            if (!cl.getSuperclass().equals(Object.class)) {
                type.addSupertype(new Supertype(new ClassType(cl.getSuperclass())));
            }
            types.put(cl.getName(), type);
            allTypes.put(cl.getName(), type);
            return type;
        }
    }

    private EntityType createSuperType(Class<?> cl) {
        return createEntityType(cl, superTypes);
    }


    private Type getType(Class<?> cl, String propertyName) throws NoSuchMethodException {
        try {
            Field field = cl.getDeclaredField(propertyName);
            return typeFactory.create(field.getType(), field.getGenericType());
        } catch (NoSuchFieldException e) {
            String getter = "get"+BeanUtils.capitalize(propertyName);
            String bgetter = "is"+BeanUtils.capitalize(propertyName);
            for (Method method : cl.getDeclaredMethods()) {
                if ((method.getName().equals(getter) || method.getName().equals(bgetter)) 
                        && method.getParameterTypes().length == 0) {
                    return typeFactory.create(method.getReturnType(), method.getGenericReturnType());
                }
            }
            if (cl.getSuperclass().equals(Object.class)) {
                throw new IllegalArgumentException("No property found for " + cl.getName() + "." + propertyName);
            } else {
                return getType(cl.getSuperclass(), propertyName);
            }

        }
    }

    private AnnotatedElement getAnnotatedElement(Class<?> cl, String propertyName) throws NoSuchMethodException {
        try {
            return cl.getDeclaredField(propertyName);
        } catch (NoSuchFieldException e) {
            String getter = "get"+BeanUtils.capitalize(propertyName);
            String bgetter = "is"+BeanUtils.capitalize(propertyName);
            for (Method method : cl.getDeclaredMethods()) {
                if ((method.getName().equals(getter) || method.getName().equals(bgetter)) 
                        && method.getParameterTypes().length == 0) {
                    return method;
                }
            }
            if (cl.getSuperclass().equals(Object.class)) {
                throw new IllegalArgumentException("No property found for " + cl.getName() + "." + propertyName);
            } else {
                return getAnnotatedElement(cl.getSuperclass(), propertyName);
            }
        }
    }

    private void serialize(Map<String,EntityType> types, Serializer serializer) throws IOException {
        for (EntityType entityType : types.values()) {
            if (serialized.add(entityType)) {
                Type type = typeMappings.getPathType(entityType, entityType, true);
                String packageName = type.getPackageName();
                String className = packageName.length() > 0 ? (packageName + "." + type.getSimpleName()) : type.getSimpleName();
                write(serializer, className.replace('.', '/') + ".java", entityType);
            }
        }
    }

    private void write(Serializer serializer, String path, EntityType type) throws IOException {
        File targetFile = new File(targetFolder, path);
        Writer w = writerFor(targetFile);
        try{
            CodeWriter writer = new JavaWriter(w);
            serializer.serialize(type, serializerConfig, writer);
        }finally{
            w.close();
        }
    }

    private Writer writerFor(File file) {
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            logger.error("Folder " + file.getParent() + " could not be created");
        }
        try {
            return new OutputStreamWriter(new FileOutputStream(file));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}
