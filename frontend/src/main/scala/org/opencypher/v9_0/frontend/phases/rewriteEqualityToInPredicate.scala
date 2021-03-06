/*
 * Copyright © 2002-2020 Neo4j Sweden AB (http://neo4j.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opencypher.v9_0.frontend.phases

import org.opencypher.v9_0.ast.semantics.SemanticFeature
import org.opencypher.v9_0.expressions.Equals
import org.opencypher.v9_0.expressions.FunctionInvocation
import org.opencypher.v9_0.expressions.In
import org.opencypher.v9_0.expressions.ListLiteral
import org.opencypher.v9_0.expressions.Property
import org.opencypher.v9_0.expressions.Variable
import org.opencypher.v9_0.expressions.functions
import org.opencypher.v9_0.frontend.phases.factories.PlanPipelineTransformerFactory
import org.opencypher.v9_0.rewriting.conditions.SemanticInfoAvailable
import org.opencypher.v9_0.util.Rewriter
import org.opencypher.v9_0.util.StepSequencer
import org.opencypher.v9_0.util.bottomUp

case object rewriteEqualityToInPredicate extends StatementRewriter with StepSequencer.Step with PlanPipelineTransformerFactory {

  override def description: String = "normalize equality predicates into IN comparisons"

  override def instance(ignored: BaseContext): Rewriter = bottomUp(Rewriter.lift {
    // id(a) = value => id(a) IN [value]
    case predicate@Equals(func@FunctionInvocation(_, _, _, IndexedSeq(idExpr)), idValueExpr)
      if func.function == functions.Id =>
      In(func, ListLiteral(Seq(idValueExpr))(idValueExpr.position))(predicate.position)

    // Equality between two property lookups should not be rewritten
    case predicate@Equals(_:Property, _:Property) =>
      predicate

    // a.prop = value => a.prop IN [value]
    case predicate@Equals(prop@Property(id: Variable, propKeyName), idValueExpr) =>
      In(prop, ListLiteral(Seq(idValueExpr))(idValueExpr.position))(predicate.position)
  })

  override def preConditions: Set[StepSequencer.Condition] = Set.empty

  override def postConditions: Set[StepSequencer.Condition] = Set(EqualityRewrittenToIn)

  override def invalidatedConditions: Set[StepSequencer.Condition] = SemanticInfoAvailable // Introduces new AST nodes

  override def getTransformer(pushdownPropertyReads: Boolean,
                              semanticFeatures: Seq[SemanticFeature]): Transformer[BaseContext, BaseState, BaseState] = this
}
