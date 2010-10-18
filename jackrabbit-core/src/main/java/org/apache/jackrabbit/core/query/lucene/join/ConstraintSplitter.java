/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.query.lucene.join;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.query.qom.And;
import javax.jcr.query.qom.ChildNode;
import javax.jcr.query.qom.Comparison;
import javax.jcr.query.qom.Constraint;
import javax.jcr.query.qom.DescendantNode;
import javax.jcr.query.qom.DynamicOperand;
import javax.jcr.query.qom.FullTextSearchScore;
import javax.jcr.query.qom.Length;
import javax.jcr.query.qom.LowerCase;
import javax.jcr.query.qom.NodeLocalName;
import javax.jcr.query.qom.NodeName;
import javax.jcr.query.qom.Not;
import javax.jcr.query.qom.Or;
import javax.jcr.query.qom.PropertyExistence;
import javax.jcr.query.qom.PropertyValue;
import javax.jcr.query.qom.QueryObjectModelFactory;
import javax.jcr.query.qom.SameNode;
import javax.jcr.query.qom.UpperCase;

/**
 * Returns a mapped constraint that only refers to the given set of
 * selectors. The returned constraint is guaranteed to match an as small
 * as possible superset of the node tuples matched by the given original
 * constraints.
 *
 * @param constraint original constraint
 * @param selectors target selectors
 * @return mapped constraint
 * @throws RepositoryException if the constraint mapping fails
 */
class ConstraintSplitter {

    private final QueryObjectModelFactory factory;

    private final Set<String> leftSelectors;

    private final Set<String> rightSelectors;

    private final List<Constraint> leftConstraints =
        new ArrayList<Constraint>();

    private final List<Constraint> rightConstraints =
        new ArrayList<Constraint>();

    public ConstraintSplitter(
            Constraint constraint, QueryObjectModelFactory factory,
            Set<String> leftSelectors, Set<String> rightSelectors)
            throws RepositoryException {
        this.factory = factory;
        this.leftSelectors = leftSelectors;
        this.rightSelectors = rightSelectors;

        if (constraint != null) {
            split(constraint);
        }
    }

    /**
     * @return the left constraint
     */
    public Constraint getLeftConstraint() throws RepositoryException {
        return Constraints.and(factory,leftConstraints);
    }

    /**
     * @return the right constraint
     */
    public Constraint getRightConstraint() throws RepositoryException {
        return Constraints.and(factory, rightConstraints);
    }

    private void split(Constraint constraint) throws RepositoryException {
        if (constraint instanceof Not) {
            splitNot((Not) constraint);
        } else if (constraint instanceof And) {
            And and = (And) constraint;
            split(and.getConstraint1());
            split(and.getConstraint2());
        } else {
            splitBySelectors(constraint, getSelectorNames(constraint));
        }
    }

    private void splitNot(Not not) throws RepositoryException {
        Constraint constraint = not.getConstraint();
        if (constraint instanceof Not) {
            split(((Not) constraint).getConstraint());
        } else if (constraint instanceof And) {
            And and = (And) constraint;
            split(factory.or(
                    factory.not(and.getConstraint1()),
                    factory.not(and.getConstraint2())));
        } else if (constraint instanceof Or) {
            Or or = (Or) constraint;
            split(factory.and(
                    factory.not(or.getConstraint1()),
                    factory.not(or.getConstraint2())));
        } else {
            splitBySelectors(not, getSelectorNames(constraint));
        }
    }

    private void splitBySelectors(Constraint constraint, Set<String> selectors)
            throws UnsupportedRepositoryOperationException {
        if (leftSelectors.containsAll(selectors)) {
            leftConstraints.add(constraint);
        } else if (rightSelectors.containsAll(selectors)) {
            rightConstraints.add(constraint);
        } else {
            throw new UnsupportedRepositoryOperationException(
                    "Unable to split a constraint that references"
                    + " both sides of a join: " + constraint);
        }
    }

    /**
     * Returns the names of the selectors referenced by the given constraint.
     *
     * @param constraint constraint
     * @return referenced selector names
     * @throws UnsupportedRepositoryOperationException
     *         if the constraint type is unknown
     */
    private Set<String> getSelectorNames(Constraint constraint)
            throws UnsupportedRepositoryOperationException {
        if (constraint instanceof And) {
            And and = (And) constraint;
            return getSelectorNames(and.getConstraint1(), and.getConstraint2());
        } else if (constraint instanceof Or) {
            Or or = (Or) constraint;
            return getSelectorNames(or.getConstraint1(), or.getConstraint2());
        } else if (constraint instanceof Not) {
            Not not = (Not) constraint;
            return getSelectorNames(not.getConstraint());
        } else if (constraint instanceof PropertyExistence) {
            PropertyExistence pe = (PropertyExistence) constraint;
            return Collections.singleton(pe.getSelectorName());
        } else if (constraint instanceof Comparison) {
            Comparison c = (Comparison) constraint;
            return Collections.singleton(getSelectorName(c.getOperand1()));
        } else if (constraint instanceof SameNode) {
            SameNode sn = (SameNode) constraint;
            return Collections.singleton(sn.getSelectorName());
        } else if (constraint instanceof ChildNode) {
            ChildNode cn = (ChildNode) constraint;
            return Collections.singleton(cn.getSelectorName());
        } else if (constraint instanceof DescendantNode) {
            DescendantNode dn = (DescendantNode) constraint;
            return Collections.singleton(dn.getSelectorName());
        } else {
            throw new UnsupportedRepositoryOperationException(
                    "Unknown constraint type: " + constraint);
        }
    }

    /**
     * Returns the combined set of selector names referenced by the given
     * two constraint.
     *
     * @param a first constraint
     * @param b second constraint
     * @return selector names
     * @throws UnsupportedRepositoryOperationException
     *         if the constraint types are unknown
     */
    private Set<String> getSelectorNames(Constraint a, Constraint b)
            throws UnsupportedRepositoryOperationException {
        Set<String> set = new HashSet<String>();
        set.addAll(getSelectorNames(a));
        set.addAll(getSelectorNames(b));
        return set;
    }

    /**
     * Returns the selector name referenced by the given dynamic operand.
     *
     * @param operand dynamic operand
     * @return selector name
     * @throws UnsupportedRepositoryOperationException
     *         if the operand type is unknown
     */
    private String getSelectorName(DynamicOperand operand)
            throws UnsupportedRepositoryOperationException {
        if (operand instanceof FullTextSearchScore) {
            FullTextSearchScore ftss = (FullTextSearchScore) operand;
            return ftss.getSelectorName();
        } else if (operand instanceof Length) {
            Length length = (Length) operand;
            return getSelectorName(length.getPropertyValue());
        } else if (operand instanceof LowerCase) {
            LowerCase lower = (LowerCase) operand;
            return getSelectorName(lower.getOperand());
        } else if (operand instanceof NodeLocalName) {
            NodeLocalName local = (NodeLocalName) operand;
            return local.getSelectorName();
        } else if (operand instanceof NodeName) {
            NodeName name = (NodeName) operand;
            return name.getSelectorName();
        } else if (operand instanceof PropertyValue) {
            PropertyValue value = (PropertyValue) operand;
            return value.getSelectorName();
        } else if (operand instanceof UpperCase) {
            UpperCase upper = (UpperCase) operand;
            return getSelectorName(upper.getOperand());
        } else {
            throw new UnsupportedRepositoryOperationException(
                    "Unknown dynamic operand type: " + operand);
        }
    }

}
