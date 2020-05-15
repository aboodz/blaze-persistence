/*
 * Copyright 2014 - 2020 Blazebit.
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

package com.blazebit.persistence.examples.spring.hateoas.repository;

import com.blazebit.persistence.examples.spring.hateoas.model.Cat;
import com.blazebit.persistence.spring.data.repository.KeysetAwarePage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

/**
 * @author Christian Beikov
 * @since 1.5.0
 */
public interface CatRepository extends Repository<Cat, Long> {

    @EntityGraph(attributePaths = {"owner"})
    public KeysetAwarePage<Cat> findAll(Specification<Cat> specification, Pageable pageable);
}
