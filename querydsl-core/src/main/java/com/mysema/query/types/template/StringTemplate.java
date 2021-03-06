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
package com.mysema.query.types.template;

import java.util.Arrays;
import java.util.List;

import com.mysema.query.types.Expression;
import com.mysema.query.types.Template;
import com.mysema.query.types.TemplateExpression;
import com.mysema.query.types.TemplateExpressionImpl;
import com.mysema.query.types.TemplateFactory;
import com.mysema.query.types.Visitor;
import com.mysema.query.types.expr.StringExpression;

/**
 * StringTemplate defines custom String expressions
 *
 * @author tiwe
 *
 */
public class StringTemplate extends StringExpression implements TemplateExpression<String> {

    private static final long serialVersionUID = 3181686132439356614L;

    public static StringExpression create(String template, Expression<?>... args) {
        return new StringTemplate(TemplateFactory.DEFAULT.create(template), Arrays.<Expression<?>>asList(args));
    }

    public static StringExpression create(Template template, Expression<?>... args) {
        return new StringTemplate(template, Arrays.<Expression<?>>asList(args));
    }

    private final TemplateExpression<String> templateMixin;

    public StringTemplate(Template template, List<Expression<?>> args) {
        templateMixin = new TemplateExpressionImpl<String>(String.class, template, args);
    }

    @Override
    public <R,C> R accept(Visitor<R,C> v, C context) {
        return v.visit(this, context);
    }

    @Override
    public Expression<?> getArg(int index) {
        return templateMixin.getArg(index);
    }

    @Override
    public List<Expression<?>> getArgs() {
        return templateMixin.getArgs();
    }

    @Override
    public Template getTemplate() {
        return templateMixin.getTemplate();
    }

    @Override
    public boolean equals(Object o) {
        return templateMixin.equals(o);
    }

    @Override
    public int hashCode() {
        return getType().hashCode();
    }

}
