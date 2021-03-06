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
package com.mysema.query.collections;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import com.mysema.query.QueryException;
import com.mysema.query.support.SerializerBase;
import com.mysema.query.types.Constant;
import com.mysema.query.types.ConstantImpl;
import com.mysema.query.types.Expression;
import com.mysema.query.types.FactoryExpression;
import com.mysema.query.types.Operator;
import com.mysema.query.types.Ops;
import com.mysema.query.types.Path;
import com.mysema.query.types.PathType;
import com.mysema.query.types.SubQueryExpression;
import com.mysema.query.types.Template;
import com.mysema.util.BeanUtils;

/**
 * ColQuerySerializer is a Serializer implementation for the Java language
 *
 * @author tiwe
 */
public final class ColQuerySerializer extends SerializerBase<ColQuerySerializer> {

    public ColQuerySerializer(ColQueryTemplates templates) {
        super(templates);
    }

    @Override
    public Void visit(Path<?> path, Void context) {
        PathType pathType = path.getMetadata().getPathType();

        if (pathType == PathType.PROPERTY) {
            // TODO : move this to PathMetadata ?!?
            String prefix = "get";
            if (path.getType() != null && path.getType().equals(Boolean.class)) {
                prefix = "is";
            }
            String property = path.getMetadata().getExpression().toString();      
            String accessor = prefix + BeanUtils.capitalize(property);
            Class<?> parentType = path.getMetadata().getParent().getType();
            try {
                // getter
                Method m = getMethod(parentType, accessor);
                if (m != null && Modifier.isPublic(m.getModifiers())) {                    
                    handle((Expression<?>) path.getMetadata().getParent());
                    append(".").append(accessor).append("()");    
                } else {
                    // field
                    Field f = getField(parentType, property);
                    if (f != null && Modifier.isPublic(f.getModifiers())) {
                        handle((Expression<?>) path.getMetadata().getParent());
                        append(".").append(property);
                    } else {
                        // field access by reflection
                        append(ColQueryFunctions.class.getName() + ".<");
                        append(((Class)path.getType()).getName()).append(">get(");
                        handle((Expression<?>) path.getMetadata().getParent());
                        append(", \""+property+"\")");
                    }
                }                
            } catch (Exception e) {
                throw new QueryException(e);
            }

        } else {
            List<Expression<?>> args = new ArrayList<Expression<?>>(2);
            if (path.getMetadata().getParent() != null) {
                args.add((Expression<?>)path.getMetadata().getParent());
            }
            args.add(path.getMetadata().getExpression());
            Template template = getTemplate(pathType);
            for (Template.Element element : template.getElements()) {
                if (element.getStaticText() != null) {
                    append(element.getStaticText());
                } else if (element.isAsString()) {
                    append(args.get(element.getIndex()).toString());
                } else {
                    handle(args.get(element.getIndex()));
                }
            }
        }
        return null;

    }
    
    private Method getMethod(Class<?> owner, String method) {
        try {
            return owner.getMethod(method);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private Field getField(Class<?> owner, String field) {
        try {
            return owner.getField(field);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
    
    @Override
    public Void visit(SubQueryExpression<?> expr, Void context) {
        throw new IllegalArgumentException("Not supported");
    }

    private void visitCast(Operator<?> operator, Expression<?> source, Class<?> targetType) {
        if (Number.class.isAssignableFrom(source.getType()) && !Constant.class.isInstance(source)) {
            append("new ").append(source.getType().getSimpleName()).append("(");
            handle(source);
            append(")");
        } else {
            handle(source);
        }

        if (Byte.class.equals(targetType)) {
            append(".byteValue()");
        } else if (Double.class.equals(targetType)) {
            append(".doubleValue()");
        } else if (Float.class.equals(targetType)) {
            append(".floatValue()");
        } else if (Integer.class.equals(targetType)) {
            append(".intValue()");
        } else if (Long.class.equals(targetType)) {
            append(".longValue()");
        } else if (Short.class.equals(targetType)) {
            append(".shortValue()");
        } else if (String.class.equals(targetType)) {
            append(".toString()");
        } else {
            throw new IllegalArgumentException("Unsupported cast type " + targetType.getName());
        }
    }

    @Override
    protected void visitOperation(Class<?> type, Operator<?> operator, List<? extends Expression<?>> args) {
        if (args.size() == 2
                && Number.class.isAssignableFrom(args.get(0).getType())
                && Number.class.isAssignableFrom(args.get(1).getType())){

            if (operator == Ops.AFTER){
                handle(args.get(0)).append(" > ").handle(args.get(1));
                return;
            }else if (operator == Ops.BEFORE){
                handle(args.get(0)).append(" < ").handle(args.get(1));
                return;
            }else if (operator == Ops.AOE){
                handle(args.get(0)).append(" >= ").handle(args.get(1));
                return;
            }else if (operator == Ops.BOE){
                handle(args.get(0)).append(" <= ").handle(args.get(1));
                return;
            }
            // TODO : Ops.BETWEEN
        }

        if (operator == Ops.STRING_CAST) {
            visitCast(operator, args.get(0), String.class);
        } else if (operator == Ops.NUMCAST) {
            visitCast(operator, args.get(0), (Class<?>) ((Constant<?>) args.get(1)).getConstant());
        } else {
            super.visitOperation(type, operator, args);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Void visit(FactoryExpression<?> expr, Void context) {
        handle(new ConstantImpl(expr));
        append(".newInstance(");
        handle(", ", expr.getArgs());
        append(")");
        return null;
    }

}
