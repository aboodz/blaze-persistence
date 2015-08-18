/*
 * Copyright 2014 Blazebit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.blazebit.persistence.view.collections.embeddable.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.Mapping;
import com.blazebit.persistence.view.collections.entity.DocumentForElementCollections;
import com.blazebit.persistence.view.collections.entity.PersonForElementCollections;

/**
 *
 * @author Christian Beikov
 * @since 1.0
 */
@EntityView(DocumentForElementCollections.class)
public abstract class EmbeddableDocumentListSetMapView implements EmbeddableDocumentCollectionsView {

    @Mapping("personList")
    public abstract List<PersonForElementCollections> getA();

    @Mapping("partners")
    public abstract Set<PersonForElementCollections> getB();

    @Mapping("contacts")
    public abstract Map<Integer, PersonForElementCollections> getC();

    @Override
    public Map<Integer, PersonForElementCollections> getContacts() {
        return getC();
    }

    @Override
    public Set<PersonForElementCollections> getPartners() {
        return getB();
    }

    @Override
    public List<PersonForElementCollections> getPersonList() {
        return getA();
    }
}