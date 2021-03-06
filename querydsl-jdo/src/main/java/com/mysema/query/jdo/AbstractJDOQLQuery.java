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
package com.mysema.query.jdo;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mysema.commons.lang.CloseableIterator;
import com.mysema.commons.lang.IteratorAdapter;
import com.mysema.query.DefaultQueryMetadata;
import com.mysema.query.NonUniqueResultException;
import com.mysema.query.QueryException;
import com.mysema.query.QueryMetadata;
import com.mysema.query.QueryModifiers;
import com.mysema.query.SearchResults;
import com.mysema.query.support.ProjectableQuery;
import com.mysema.query.types.EntityPath;
import com.mysema.query.types.Expression;
import com.mysema.query.types.FactoryExpression;
import com.mysema.query.types.QTuple;

/**
 * Abstract base class for custom implementations of the JDOQLQuery interface.
 *
 * @author tiwe
 *
 * @param <Q>
 */
public abstract class AbstractJDOQLQuery<Q extends AbstractJDOQLQuery<Q>> extends ProjectableQuery<Q>{

    private static final Logger logger = LoggerFactory.getLogger(JDOQLQueryImpl.class);

    private final Closeable closeable = new Closeable() {
        @Override
        public void close() throws IOException {
            AbstractJDOQLQuery.this.close();
        }
    };

    private final boolean detach;

    private List<Object> orderedConstants = new ArrayList<Object>();

    @Nullable
    private final PersistenceManager persistenceManager;

    private final List<Query> queries = new ArrayList<Query>(2);

    private final JDOQLTemplates templates;

    protected final Set<String> fetchGroups = new HashSet<String>();

    @Nullable
    protected Integer maxFetchDepth;

    public AbstractJDOQLQuery(@Nullable PersistenceManager persistenceManager) {
        this(persistenceManager, JDOQLTemplates.DEFAULT, new DefaultQueryMetadata(), false);
    }

    @SuppressWarnings("unchecked")
    public AbstractJDOQLQuery(
            @Nullable PersistenceManager persistenceManager,
            JDOQLTemplates templates,
            QueryMetadata metadata, boolean detach) {
        super(new JDOQLQueryMixin<Q>(metadata));
        this.queryMixin.setSelf((Q) this);
        this.templates = templates;
        this.persistenceManager = persistenceManager;
        this.detach = detach;
    }

    @SuppressWarnings("unchecked")
    public Q addFetchGroup(String fetchGroupName) {
        fetchGroups.add(fetchGroupName);
        return (Q)this;
    }

    public void close() {
        for (Query query : queries) {
            query.closeAll();
        }
    }

    @Override
    public long count() {
        Query query = createQuery(true);
        query.setUnique(true);
        reset();
        Long rv = (Long) execute(query);
        if (rv != null) {
            return rv.longValue();
        } else {
            throw new QueryException("Query returned null");
        }
    }

    @Override
    public boolean exists() {
        boolean rv = limit(1).uniqueResult(getSource()) != null;
        close();
        return rv;
    }

    private Expression<?> getSource() {
        return queryMixin.getMetadata().getJoins().get(0).getTarget();
    }

    private Query createQuery(boolean forCount) {
        Expression<?> source = getSource();

        // serialize
        JDOQLSerializer serializer = new JDOQLSerializer(getTemplates(), source);
        serializer.serialize(queryMixin.getMetadata(), forCount, false);

        logQuery(serializer.toString());

        // create Query
        Query query = persistenceManager.newQuery(serializer.toString());
        orderedConstants = serializer.getConstants();
        queries.add(query);

        if (!forCount) {
            List<? extends Expression<?>> projection = queryMixin.getMetadata().getProjection();
            Class<?> exprType = projection.get(0).getClass();
            if (exprType.equals(QTuple.class)) {
                query.setResultClass(JDOTuple.class);
            } else if (FactoryExpression.class.isAssignableFrom(exprType)) {
                query.setResultClass(projection.get(0).getType());
            }

            if (!fetchGroups.isEmpty()) {
                query.getFetchPlan().setGroups(fetchGroups);
            }
            if (maxFetchDepth != null) {
                query.getFetchPlan().setMaxFetchDepth(maxFetchDepth);
            }
        }

        return query;
    }

    protected void logQuery(String queryString) {
        if (logger.isDebugEnabled()) {
            logger.debug(queryString.replace('\n', ' '));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T detach(T results) {
        if (results instanceof Collection) {
            return (T) persistenceManager.detachCopyAll(results);
        } else {
            return persistenceManager.detachCopy(results);
        }
    }

    @Nullable
    private Object execute(Query query) {
        Object rv;
        if (!orderedConstants.isEmpty()) {
            rv = query.executeWithArray(orderedConstants.toArray());
        } else {
            rv = query.execute();
        }
        if (isDetach()) {
            rv = detach(rv);
        }
        return rv;
    }

    public Q from(EntityPath<?>... args) {
        return queryMixin.from(args);
    }

    public QueryMetadata getMetadata() {
        return queryMixin.getMetadata();
    }

    public JDOQLTemplates getTemplates() {
        return templates;
    }

    public boolean isDetach() {
        return detach;
    }

    public CloseableIterator<Object[]> iterate(Expression<?>[] args) {
        return new IteratorAdapter<Object[]>(list(args).iterator(), closeable);
    }

    public <RT> CloseableIterator<RT> iterate(Expression<RT> projection) {
        return new IteratorAdapter<RT>(list(projection).iterator(), closeable);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> list(Expression<?>[] args) {
        queryMixin.addToProjection(args);
        Object rv = execute(createQuery(false));
        reset();
        return (rv instanceof List) ? ((List<Object[]>)rv) : Collections.singletonList((Object[])rv);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <RT> List<RT> list(Expression<RT> expr) {
        queryMixin.addToProjection(expr);
        Object rv = execute(createQuery(false));
        reset();
        return rv instanceof List ? (List<RT>)rv : Collections.singletonList((RT)rv);
    }

    @SuppressWarnings("unchecked")
    public <RT> SearchResults<RT> listResults(Expression<RT> expr) {
        queryMixin.addToProjection(expr);
        Query countQuery = createQuery(true);
        countQuery.setUnique(true);
        countQuery.setResult("count(this)");
        long total = (Long) execute(countQuery);
        if (total > 0) {
            QueryModifiers modifiers = queryMixin.getMetadata().getModifiers();
            Query query = createQuery(false);
            reset();
            return new SearchResults<RT>((List<RT>) execute(query), modifiers, total);
        } else {
            reset();
            return SearchResults.emptyResults();
        }
    }

    private void reset() {
        queryMixin.getMetadata().reset();
    }

    @SuppressWarnings("unchecked")
    public Q setMaxFetchDepth(int depth) {
        maxFetchDepth = depth;
        return (Q)this;
    }

    @Override
    public String toString() {
        if (!queryMixin.getMetadata().getJoins().isEmpty()) {
            Expression<?> source = getSource();
            JDOQLSerializer serializer = new JDOQLSerializer(getTemplates(), source);
            serializer.serialize(queryMixin.getMetadata(), false, false);
            return serializer.toString().trim();
        } else {
            return super.toString();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    @Nullable
    public <RT> RT uniqueResult(Expression<RT> expr) {
        queryMixin.addToProjection(expr);
        return (RT)uniqueResult();
    }
    
    @Override
    @Nullable
    public Object[] uniqueResult(Expression<?>[] args) {
        queryMixin.addToProjection(args);
        Object obj = uniqueResult();
        if (obj != null) {
            return obj.getClass().isArray() ? (Object[])obj : new Object[]{obj};    
        } else {
            return null;
        }                
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private Object uniqueResult() {
        if (getMetadata().getModifiers().getLimit() == null) {
            limit(2);
        }
        Query query = createQuery(false);
        reset();
        Object rv = execute(query);
        if (rv instanceof List) {
            List<?> list = (List)rv;
            if (!list.isEmpty()) {
                if (list.size() > 1) {
                    throw new NonUniqueResultException();
                }
                return list.get(0);
            } else {
                return null;
            }
        } else {
            return rv;
        }
    }

}
