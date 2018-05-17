/*
 * Copyright © 2002-2018 Neo4j Sweden AB (http://neo4j.com)
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
package org.opencypher.v9_1.parser

import org.opencypher.v9_1.util.InputPosition
import org.opencypher.v9_1.{expressions => ast}
import org.opencypher.v9_1.expressions
import org.opencypher.v9_1.expressions._
import org.parboiled.scala._

import scala.collection.mutable.ListBuffer

trait Expressions extends Parser
  with Literals
  with Patterns
  with Base {

  // Precedence loosely based on http://en.wikipedia.org/wiki/Operators_in_C_and_C%2B%2B#Operator_precedence

  def Expression = Expression12

  private def Expression12: Rule1[org.opencypher.v9_1.expressions.Expression] = rule("an expression") {
    Expression11 ~ zeroOrMore(WS ~ (
        group(keyword("OR") ~~ Expression11) ~~>> (Or(_: org.opencypher.v9_1.expressions.Expression, _))
    ): ReductionRule1[org.opencypher.v9_1.expressions.Expression, org.opencypher.v9_1.expressions.Expression])
  }

  private def Expression11: Rule1[org.opencypher.v9_1.expressions.Expression] = rule("an expression") {
    Expression10 ~ zeroOrMore(WS ~ (
        group(keyword("XOR") ~~ Expression10) ~~>> (expressions.Xor(_: org.opencypher.v9_1.expressions.Expression, _))
    ): ReductionRule1[org.opencypher.v9_1.expressions.Expression, org.opencypher.v9_1.expressions.Expression])
  }

  private def Expression10: Rule1[org.opencypher.v9_1.expressions.Expression] = rule("an expression") {
    Expression9 ~ zeroOrMore(WS ~ (
        group(keyword("AND") ~~ Expression9) ~~>> (And(_: org.opencypher.v9_1.expressions.Expression, _))
    ): ReductionRule1[org.opencypher.v9_1.expressions.Expression, org.opencypher.v9_1.expressions.Expression])
  }

  private def Expression9: Rule1[org.opencypher.v9_1.expressions.Expression] = rule("an expression") (
      group(keyword("NOT") ~~ Expression9) ~~>> (expressions.Not(_))
    | Expression8
  )

  private def Expression8: Rule1[org.opencypher.v9_1.expressions.Expression] = rule("comparison expression") {
    val produceComparisons: (org.opencypher.v9_1.expressions.Expression, List[PartialComparison]) => InputPosition => org.opencypher.v9_1.expressions.Expression = comparisons
    Expression7 ~ zeroOrMore(WS ~ PartialComparisonExpression) ~~>> produceComparisons
  }

  private case class PartialComparison(op: (org.opencypher.v9_1.expressions.Expression, org.opencypher.v9_1.expressions.Expression) => (InputPosition) => org.opencypher.v9_1.expressions.Expression,
                                       expr: org.opencypher.v9_1.expressions.Expression, pos: InputPosition) {
    def apply(lhs: org.opencypher.v9_1.expressions.Expression) = op(lhs, expr)(pos)
  }

  private def PartialComparisonExpression: Rule1[PartialComparison] = (
      group(operator("=") ~~ Expression7) ~~>> { expr: org.opencypher.v9_1.expressions.Expression => pos: InputPosition => PartialComparison(eq, expr, pos) }
    | group(operator("<>") ~~ Expression7) ~~>> { expr: org.opencypher.v9_1.expressions.Expression => pos: InputPosition => PartialComparison(ne, expr, pos) }
    | group(operator("!=") ~~ Expression7) ~~>> { expr: org.opencypher.v9_1.expressions.Expression => pos: InputPosition => PartialComparison(bne, expr, pos) }
    | group(operator("<") ~~ Expression7) ~~>> { expr: org.opencypher.v9_1.expressions.Expression => pos: InputPosition => PartialComparison(lt, expr, pos) }
    | group(operator(">") ~~ Expression7) ~~>> { expr: org.opencypher.v9_1.expressions.Expression => pos: InputPosition => PartialComparison(gt, expr, pos) }
    | group(operator("<=") ~~ Expression7) ~~>> { expr: org.opencypher.v9_1.expressions.Expression => pos: InputPosition => PartialComparison(lte, expr, pos) }
    | group(operator(">=") ~~ Expression7) ~~>> { expr: org.opencypher.v9_1.expressions.Expression => pos: InputPosition => PartialComparison(gte, expr, pos) } )

  private def eq(lhs:org.opencypher.v9_1.expressions.Expression, rhs:org.opencypher.v9_1.expressions.Expression): InputPosition => org.opencypher.v9_1.expressions.Expression = expressions.Equals(lhs, rhs)
  private def ne(lhs:org.opencypher.v9_1.expressions.Expression, rhs:org.opencypher.v9_1.expressions.Expression): InputPosition => org.opencypher.v9_1.expressions.Expression = expressions.NotEquals(lhs, rhs)
  private def bne(lhs:org.opencypher.v9_1.expressions.Expression, rhs:org.opencypher.v9_1.expressions.Expression): InputPosition => org.opencypher.v9_1.expressions.Expression = expressions.InvalidNotEquals(lhs, rhs)
  private def lt(lhs:org.opencypher.v9_1.expressions.Expression, rhs:org.opencypher.v9_1.expressions.Expression): InputPosition => org.opencypher.v9_1.expressions.Expression = expressions.LessThan(lhs, rhs)
  private def gt(lhs:org.opencypher.v9_1.expressions.Expression, rhs:org.opencypher.v9_1.expressions.Expression): InputPosition => org.opencypher.v9_1.expressions.Expression = expressions.GreaterThan(lhs, rhs)
  private def lte(lhs:org.opencypher.v9_1.expressions.Expression, rhs:org.opencypher.v9_1.expressions.Expression): InputPosition => org.opencypher.v9_1.expressions.Expression = expressions.LessThanOrEqual(lhs, rhs)
  private def gte(lhs:org.opencypher.v9_1.expressions.Expression, rhs:org.opencypher.v9_1.expressions.Expression): InputPosition => org.opencypher.v9_1.expressions.Expression = expressions.GreaterThanOrEqual(lhs, rhs)

  private def comparisons(first: org.opencypher.v9_1.expressions.Expression, rest: List[PartialComparison]): InputPosition => org.opencypher.v9_1.expressions.Expression = {
    rest match {
      case Nil => _ => first
      case second :: Nil => _ => second(first)
      case more =>
        var lhs = first
        val result = ListBuffer.empty[org.opencypher.v9_1.expressions.Expression]
        for (rhs <- more) {
          result.append(rhs(lhs))
          lhs = rhs.expr
        }
        Ands(Set(result: _*))
    }
  }

  private def Expression7: Rule1[org.opencypher.v9_1.expressions.Expression] = rule("an expression") {
    Expression6 ~ zeroOrMore(WS ~ (
      group(operator("+") ~~ Expression6) ~~>> (ast.Add(_: org.opencypher.v9_1.expressions.Expression, _))
        | group(operator("-") ~~ Expression6) ~~>> (ast.Subtract(_: org.opencypher.v9_1.expressions.Expression, _))
      ))
  }

  private def Expression6: Rule1[org.opencypher.v9_1.expressions.Expression] = rule("an expression") {
    Expression5 ~ zeroOrMore(WS ~ (
      group(operator("*") ~~ Expression5) ~~>> (ast.Multiply(_: org.opencypher.v9_1.expressions.Expression, _))
        | group(operator("/") ~~ Expression5) ~~>> (ast.Divide(_: org.opencypher.v9_1.expressions.Expression, _))
        | group(operator("%") ~~ Expression5) ~~>> (ast.Modulo(_: org.opencypher.v9_1.expressions.Expression, _))
      ))
  }

  private def Expression5: Rule1[org.opencypher.v9_1.expressions.Expression] = rule("an expression") {
    Expression4 ~ zeroOrMore(WS ~ (
      group(operator("^") ~~ Expression4) ~~>> (ast.Pow(_: org.opencypher.v9_1.expressions.Expression, _))
      ))
  }

  private def Expression4: Rule1[org.opencypher.v9_1.expressions.Expression] = rule("an expression") (
      Expression3
    | group(operator("+") ~~ Expression4) ~~>> (ast.UnaryAdd(_))
    | group(operator("-") ~~ Expression4) ~~>> (ast.UnarySubtract(_))
  )

  private def Expression3: Rule1[org.opencypher.v9_1.expressions.Expression] = rule("an expression") {
    Expression2 ~ zeroOrMore(WS ~ (
      group(operator("=~") ~~ Expression2) ~~>> (expressions.RegexMatch(_: org.opencypher.v9_1.expressions.Expression, _))
      | group(keyword("IN") ~~ Expression2) ~~>> (expressions.In(_: org.opencypher.v9_1.expressions.Expression, _))
      | group(keyword("STARTS WITH") ~~ Expression2) ~~>> (expressions.StartsWith(_: org.opencypher.v9_1.expressions.Expression, _))
      | group(keyword("ENDS WITH") ~~ Expression2) ~~>> (expressions.EndsWith(_: org.opencypher.v9_1.expressions.Expression, _))
      | group(keyword("CONTAINS") ~~ Expression2) ~~>> (expressions.Contains(_: org.opencypher.v9_1.expressions.Expression, _))
      | keyword("IS NULL") ~~>> (expressions.IsNull(_: org.opencypher.v9_1.expressions.Expression))
      | keyword("IS NOT NULL") ~~>> (expressions.IsNotNull(_: org.opencypher.v9_1.expressions.Expression))
    ): ReductionRule1[org.opencypher.v9_1.expressions.Expression, org.opencypher.v9_1.expressions.Expression])
  }

  private def Expression2: Rule1[org.opencypher.v9_1.expressions.Expression] = rule("an expression") {
    Expression1 ~ zeroOrMore(WS ~ (
        PropertyLookup
      | NodeLabels ~~>> (ast.HasLabels(_: org.opencypher.v9_1.expressions.Expression, _))
      |  "[" ~~ Expression ~~ "]" ~~>> (ast.ContainerIndex(_: org.opencypher.v9_1.expressions.Expression, _))
      | "[" ~~ optional(Expression) ~~ ".." ~~ optional(Expression) ~~ "]" ~~>> (ast.ListSlice(_: org.opencypher.v9_1.expressions.Expression, _, _))
    ))
  }

  private def Expression1: Rule1[org.opencypher.v9_1.expressions.Expression] = rule("an expression") (
      NumberLiteral
    | StringLiteral
    | Parameter
    | keyword("TRUE") ~ push(ast.True()(_))
    | keyword("FALSE") ~ push(ast.False()(_))
    | keyword("NULL") ~ push(ast.Null()(_))
    | CaseExpression
    | group(keyword("COUNT") ~~ "(" ~~ "*" ~~ ")") ~ push(ast.CountStar()(_))
    | MapLiteral
    | MapProjection
    | ListComprehension
    | PatternComprehension
    | group("[" ~~ zeroOrMore(Expression, separator = CommaSep) ~~ "]") ~~>> (ast.ListLiteral(_))
    | group(keyword("FILTER") ~~ "(" ~~ FilterExpression ~~ ")") ~~>> (ast.FilterExpression(_, _, _))
    | group(keyword("EXTRACT") ~~ "(" ~~ FilterExpression ~ optional(WS ~ "|" ~~ Expression) ~~ ")") ~~>> (ast.ExtractExpression(_, _, _, _))
    | group(keyword("REDUCE") ~~ "(" ~~ Variable ~~ "=" ~~ Expression ~~ "," ~~ IdInColl ~~ "|" ~~ Expression ~~ ")") ~~>> (ast.ReduceExpression(_, _, _, _, _))
    | group(keyword("ALL") ~~ "(" ~~ FilterExpression ~~ ")") ~~>> (ast.AllIterablePredicate(_, _, _))
    | group(keyword("ANY") ~~ "(" ~~ FilterExpression ~~ ")") ~~>> (ast.AnyIterablePredicate(_, _, _))
    | group(keyword("NONE") ~~ "(" ~~ FilterExpression ~~ ")") ~~>> (ast.NoneIterablePredicate(_, _, _))
    | group(keyword("SINGLE") ~~ "(" ~~ FilterExpression ~~ ")") ~~>> (ast.SingleIterablePredicate(_, _, _))
    | ShortestPathPattern ~~> expressions.ShortestPathExpression
    | RelationshipsPattern ~~> PatternExpression
    | parenthesizedExpression
    | FunctionInvocation
    | Variable
  )

  def parenthesizedExpression: Rule1[org.opencypher.v9_1.expressions.Expression] = "(" ~~ Expression ~~ ")"

  def PropertyExpression: Rule1[org.opencypher.v9_1.expressions.Property] = rule {
    Expression1 ~ oneOrMore(WS ~ PropertyLookup)
  }

  def PropertyLookup: ReductionRule1[org.opencypher.v9_1.expressions.Expression, org.opencypher.v9_1.expressions.Property] = rule("'.'") {
    operator(".") ~~ (PropertyKeyName ~~>> (ast.Property(_: org.opencypher.v9_1.expressions.Expression, _)))
  }

  private def FilterExpression: Rule3[Variable, org.opencypher.v9_1.expressions.Expression, Option[org.opencypher.v9_1.expressions.Expression]] =
    IdInColl ~ optional(WS ~ keyword("WHERE") ~~ Expression)

  private def IdInColl: Rule2[Variable, org.opencypher.v9_1.expressions.Expression] =
    Variable ~~ keyword("IN") ~~ Expression

  def FunctionInvocation: Rule1[org.opencypher.v9_1.expressions.FunctionInvocation] = rule("a function") {
    ((group(Namespace ~~ FunctionName ~~ "(" ~~
      (keyword("DISTINCT") ~ push(true) | EMPTY ~ push(false)) ~~
      zeroOrMore(Expression, separator = CommaSep) ~~ ")"
    ) ~~> (_.toIndexedSeq)) memoMismatches) ~~>> (ast.FunctionInvocation(_, _, _, _))
  }

  def ListComprehension: Rule1[org.opencypher.v9_1.expressions.ListComprehension] = rule("[") {
    group("[" ~~ FilterExpression ~ optional(WS ~ "|" ~~ Expression) ~~ "]") ~~>> (ast.ListComprehension(_, _, _, _))
  }

  def PatternComprehension: Rule1[org.opencypher.v9_1.expressions.PatternComprehension] = rule("[") {
    group("[" ~~ optional(Variable ~~ operator("=")) ~~ RelationshipsPattern ~ optional(WS ~ keyword("WHERE") ~~ Expression) ~~ "|" ~~ Expression ~~ "]") ~~>> (ast.PatternComprehension(_, _, _, _))
  }

  def CaseExpression: Rule1[org.opencypher.v9_1.expressions.CaseExpression] = rule("CASE") {
    (group((
        keyword("CASE") ~~ push(None) ~ oneOrMore(WS ~ CaseAlternatives)
      | keyword("CASE") ~~ Expression ~~> (Some(_)) ~ oneOrMore(WS ~ CaseAlternatives)
      ) ~ optional(WS ~
        keyword("ELSE") ~~ Expression
      ) ~~ keyword("END")
    ) memoMismatches) ~~>> (ast.CaseExpression(_, _, _))
  }

  private def CaseAlternatives: Rule2[org.opencypher.v9_1.expressions.Expression, org.opencypher.v9_1.expressions.Expression] = rule("WHEN") {
    keyword("WHEN") ~~ Expression ~~ keyword("THEN") ~~ Expression
  }
}
