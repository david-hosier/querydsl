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
package com.mysema.query.jpa;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.mysema.query.jpa.domain.QCat;

public class JPQLSubQueryTest {
    
    @Test
    public void Multiple_Projections(){
        JPQLSubQuery query = new JPQLSubQuery();
        query.from(QCat.cat);
        assertEquals(1, query.list(QCat.cat).getMetadata().getProjection().size());
        assertEquals(1, query.list(QCat.cat).getMetadata().getProjection().size());
    }

}
