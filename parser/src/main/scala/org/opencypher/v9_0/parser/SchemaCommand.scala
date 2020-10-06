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
package org.opencypher.v9_0.parser

import org.opencypher.v9_0.ast
import org.opencypher.v9_0.ast.IfExistsDoNothing
import org.opencypher.v9_0.ast.IfExistsInvalidSyntax
import org.opencypher.v9_0.ast.IfExistsReplace
import org.opencypher.v9_0.ast.IfExistsThrowError
import org.opencypher.v9_0.expressions.Expression
import org.opencypher.v9_0.expressions.LabelName
import org.opencypher.v9_0.expressions.Property
import org.opencypher.v9_0.expressions.Variable
import org.parboiled.scala.Parser
import org.parboiled.scala.Rule1
import org.parboiled.scala.Rule3
import org.parboiled.scala.Rule4
import org.parboiled.scala.group

trait SchemaCommand extends Parser
  with Expressions
  with Literals
  with Base
  with ProcedureCalls
  with GraphSelection {

  def SchemaCommand: Rule1[ast.SchemaCommand] = rule(
    optional(UseGraph) ~~ (
      CreateUniqueConstraint
      | CreateUniqueCompositeConstraint
      | CreateNodeKeyConstraint
      | CreateNodePropertyExistenceConstraint
      | CreateRelationshipPropertyExistenceConstraint
      | CreateIndexOldSyntax
      | CreateIndex
      | DropUniqueConstraint
      | DropUniqueCompositeConstraint
      | DropNodeKeyConstraint
      | DropNodePropertyExistenceConstraint
      | DropRelationshipPropertyExistenceConstraint
      | DropConstraintOnName
      | DropIndex
      | DropIndexOnName) ~~> ((use, command) => command.withGraph(use))
  )

  def VariablePropertyExpression: Rule1[Property] = rule("single property expression from variable") {
    Variable ~ PropertyLookup
  }

  def VariablePropertyExpressions: Rule1[Seq[Property]] = rule("multiple property expressions from variable") {
    oneOrMore(WS ~ VariablePropertyExpression, separator = CommaSep)
  }

  def options: Rule1[Map[String, Expression]] = rule {
    keyword("OPTIONS") ~~ group(ch('{') ~~ zeroOrMore(SymbolicNameString ~~ ch(':') ~~ Expression, separator = CommaSep) ~~ ch('}')) ~~>> (l => _ => l.toMap)
  }

  def CreateIndexOldSyntax: Rule1[ast.CreateIndexOldSyntax] = rule {
    group(keyword("CREATE INDEX ON") ~~ NodeLabel ~~ "(" ~~ PropertyKeyNames ~~ ")") ~~>> (ast.CreateIndexOldSyntax(_, _))
  }

  def CreateIndex: Rule1[ast.CreateIndex] = rule {
    // without name
    group((keyword("CREATE OR REPLACE BTREE INDEX IF NOT EXISTS FOR") | keyword("CREATE OR REPLACE INDEX IF NOT EXISTS FOR")) ~~ IndexPatternSyntax) ~~>>
      ((variable, label, properties, options) => ast.CreateIndex(variable, label, properties.toList, None, IfExistsInvalidSyntax, options)) |
    group((keyword("CREATE OR REPLACE BTREE INDEX FOR") | keyword("CREATE OR REPLACE INDEX FOR")) ~~ IndexPatternSyntax) ~~>>
      ((variable, label, properties, options) => ast.CreateIndex(variable, label, properties.toList, None, IfExistsReplace, options)) |
    group((keyword("CREATE BTREE INDEX IF NOT EXISTS FOR") | keyword("CREATE INDEX IF NOT EXISTS FOR")) ~~ IndexPatternSyntax) ~~>>
      ((variable, label, properties, options) => ast.CreateIndex(variable, label, properties.toList, None, IfExistsDoNothing, options)) |
    group( (keyword("CREATE BTREE INDEX FOR") | keyword("CREATE INDEX FOR")) ~~ IndexPatternSyntax) ~~>>
      ((variable, label, properties, options) => ast.CreateIndex(variable, label, properties.toList, None, IfExistsThrowError, options)) |
    // with name
    group((keyword("CREATE OR REPLACE BTREE INDEX") | keyword("CREATE OR REPLACE INDEX")) ~~ SymbolicNameString ~~ keyword("IF NOT EXISTS FOR") ~~ IndexPatternSyntax) ~~>>
      ((name, variable, label, properties, options) => ast.CreateIndex(variable, label, properties.toList, Some(name), IfExistsInvalidSyntax, options)) |
    group((keyword("CREATE OR REPLACE BTREE INDEX") | keyword("CREATE OR REPLACE INDEX")) ~~ SymbolicNameString ~~ keyword("FOR") ~~ IndexPatternSyntax) ~~>>
      ((name, variable, label, properties, options) => ast.CreateIndex(variable, label, properties.toList, Some(name), IfExistsReplace, options)) |
    group((keyword("CREATE BTREE INDEX") | keyword("CREATE INDEX")) ~~ SymbolicNameString ~~ keyword("IF NOT EXISTS FOR") ~~ IndexPatternSyntax) ~~>>
      ((name, variable, label, properties, options) => ast.CreateIndex(variable, label, properties.toList, Some(name), IfExistsDoNothing, options)) |
    group((keyword("CREATE BTREE INDEX") | keyword("CREATE INDEX"))~~ SymbolicNameString ~~ keyword("FOR") ~~ IndexPatternSyntax) ~~>>
      ((name, variable, label, properties, options) => ast.CreateIndex(variable, label, properties.toList, Some(name), IfExistsThrowError, options))
  }

  def IndexPatternSyntax: Rule4[Variable, LabelName, Seq[Property], Map[String, Expression]] = rule {
    group("(" ~~ Variable ~~ NodeLabel ~~ ")" ~~ keyword("ON") ~~ "(" ~~ VariablePropertyExpressions ~~ ")" ~~ options) |
    group("(" ~~ Variable ~~ NodeLabel ~~ ")" ~~ keyword("ON") ~~ "(" ~~ VariablePropertyExpressions ~~ ")") ~> (_ => Map.empty)
  }

  def DropIndex: Rule1[ast.DropIndex] = rule {
    group(keyword("DROP INDEX ON") ~~ NodeLabel ~~ "(" ~~ PropertyKeyNames ~~ ")") ~~>> (ast.DropIndex(_, _))
  }

  def DropIndexOnName: Rule1[ast.DropIndexOnName] = rule {
    group(keyword("DROP INDEX") ~~ SymbolicNameString ~~ keyword("IF EXISTS")) ~~>> (ast.DropIndexOnName(_, ifExists = true)) |
    group(keyword("DROP INDEX") ~~ SymbolicNameString) ~~>> (ast.DropIndexOnName(_, ifExists = false))
  }

  def CreateUniqueConstraint: Rule1[ast.CreateUniquePropertyConstraint] = rule {
    // without name
    group(keyword("CREATE OR REPLACE CONSTRAINT IF NOT EXISTS") ~~ UniqueConstraintSyntax) ~~>>
      ((variable, label, property) => ast.CreateUniquePropertyConstraint(variable, label, Seq(property), None, IfExistsInvalidSyntax)) |
    group(keyword("CREATE OR REPLACE CONSTRAINT") ~~ UniqueConstraintSyntax) ~~>>
      ((variable, label, property) => ast.CreateUniquePropertyConstraint(variable, label, Seq(property), None, IfExistsReplace)) |
    group(keyword("CREATE CONSTRAINT IF NOT EXISTS") ~~ UniqueConstraintSyntax) ~~>>
      ((variable, label, property) => ast.CreateUniquePropertyConstraint(variable, label, Seq(property), None, IfExistsDoNothing)) |
    group(keyword("CREATE CONSTRAINT") ~~ UniqueConstraintSyntax) ~~>>
      ((variable, label, property) => ast.CreateUniquePropertyConstraint(variable, label, Seq(property), None, IfExistsThrowError)) |
    // with name
    group(keyword("CREATE OR REPLACE CONSTRAINT") ~~ SymbolicNameString ~~ keyword("IF NOT EXISTS") ~~ UniqueConstraintSyntax) ~~>>
      ((name, variable, label, property) => ast.CreateUniquePropertyConstraint(variable, label, Seq(property), Some(name), IfExistsInvalidSyntax)) |
    group(keyword("CREATE OR REPLACE CONSTRAINT") ~~ SymbolicNameString ~~ UniqueConstraintSyntax) ~~>>
      ((name, variable, label, property) => ast.CreateUniquePropertyConstraint(variable, label, Seq(property), Some(name), IfExistsReplace)) |
    group(keyword("CREATE CONSTRAINT") ~~ SymbolicNameString ~~ keyword("IF NOT EXISTS") ~~ UniqueConstraintSyntax) ~~>>
      ((name, variable, label, property) => ast.CreateUniquePropertyConstraint(variable, label, Seq(property), Some(name), IfExistsDoNothing)) |
    group(keyword("CREATE CONSTRAINT") ~~ SymbolicNameString ~~ UniqueConstraintSyntax) ~~>>
      ((name, variable, label, property) => ast.CreateUniquePropertyConstraint(variable, label, Seq(property), Some(name), IfExistsThrowError))
  }

  def CreateUniqueCompositeConstraint: Rule1[ast.CreateUniquePropertyConstraint] = rule {
    // without name
    group(keyword("CREATE OR REPLACE CONSTRAINT IF NOT EXISTS") ~~ UniqueCompositeConstraintSyntax) ~~>> (ast.CreateUniquePropertyConstraint(_, _, _, None, IfExistsInvalidSyntax)) |
    group(keyword("CREATE OR REPLACE CONSTRAINT") ~~ UniqueCompositeConstraintSyntax) ~~>> (ast.CreateUniquePropertyConstraint(_, _, _, None, IfExistsReplace)) |
    group(keyword("CREATE CONSTRAINT IF NOT EXISTS") ~~ UniqueCompositeConstraintSyntax) ~~>> (ast.CreateUniquePropertyConstraint(_, _, _, None, IfExistsDoNothing)) |
    group(keyword("CREATE CONSTRAINT") ~~ UniqueCompositeConstraintSyntax) ~~>> (ast.CreateUniquePropertyConstraint(_, _, _, None, IfExistsThrowError)) |
    // with name
    group(keyword("CREATE OR REPLACE CONSTRAINT") ~~ SymbolicNameString ~~ keyword("IF NOT EXISTS") ~~ UniqueCompositeConstraintSyntax) ~~>>
      ((name, variable, labelName, properties) => ast.CreateUniquePropertyConstraint(variable, labelName, properties, Some(name), IfExistsInvalidSyntax)) |
    group(keyword("CREATE OR REPLACE CONSTRAINT") ~~ SymbolicNameString ~~ UniqueCompositeConstraintSyntax) ~~>>
      ((name, variable, labelName, properties) => ast.CreateUniquePropertyConstraint(variable, labelName, properties, Some(name), IfExistsReplace)) |
    group(keyword("CREATE CONSTRAINT") ~~ SymbolicNameString ~~ keyword("IF NOT EXISTS") ~~ UniqueCompositeConstraintSyntax) ~~>>
      ((name, variable, labelName, properties) => ast.CreateUniquePropertyConstraint(variable, labelName, properties, Some(name), IfExistsDoNothing)) |
    group(keyword("CREATE CONSTRAINT") ~~ SymbolicNameString ~~ UniqueCompositeConstraintSyntax) ~~>>
      ((name, variable, labelName, properties) => ast.CreateUniquePropertyConstraint(variable, labelName, properties, Some(name), IfExistsThrowError))
  }

  def CreateNodeKeyConstraint: Rule1[ast.CreateNodeKeyConstraint] = rule {
    // without name
    group(keyword("CREATE OR REPLACE CONSTRAINT IF NOT EXISTS") ~~ NodeKeyConstraintSyntax) ~~>> (ast.CreateNodeKeyConstraint(_, _, _, None, IfExistsInvalidSyntax)) |
    group(keyword("CREATE OR REPLACE CONSTRAINT") ~~ NodeKeyConstraintSyntax) ~~>> (ast.CreateNodeKeyConstraint(_, _, _, None, IfExistsReplace)) |
    group(keyword("CREATE CONSTRAINT IF NOT EXISTS") ~~ NodeKeyConstraintSyntax) ~~>> (ast.CreateNodeKeyConstraint(_, _, _, None, IfExistsDoNothing)) |
    group(keyword("CREATE CONSTRAINT") ~~ NodeKeyConstraintSyntax) ~~>> (ast.CreateNodeKeyConstraint(_, _, _, None, IfExistsThrowError)) |
    // with name
    group(keyword("CREATE OR REPLACE CONSTRAINT") ~~ SymbolicNameString ~~ keyword("IF NOT EXISTS") ~~ NodeKeyConstraintSyntax) ~~>>
      ((name, variable, labelName, property) => ast.CreateNodeKeyConstraint(variable, labelName, property, Some(name), IfExistsInvalidSyntax)) |
    group(keyword("CREATE OR REPLACE CONSTRAINT") ~~ SymbolicNameString ~~ NodeKeyConstraintSyntax) ~~>>
      ((name, variable, labelName, property) => ast.CreateNodeKeyConstraint(variable, labelName, property, Some(name), IfExistsReplace)) |
    group(keyword("CREATE CONSTRAINT") ~~ SymbolicNameString ~~ keyword("IF NOT EXISTS") ~~ NodeKeyConstraintSyntax) ~~>>
      ((name, variable, labelName, property) => ast.CreateNodeKeyConstraint(variable, labelName, property, Some(name), IfExistsDoNothing)) |
    group(keyword("CREATE CONSTRAINT") ~~ SymbolicNameString ~~ NodeKeyConstraintSyntax) ~~>>
      ((name, variable, labelName, property) => ast.CreateNodeKeyConstraint(variable, labelName, property, Some(name), IfExistsThrowError))
  }

  def CreateNodePropertyExistenceConstraint: Rule1[ast.CreateNodePropertyExistenceConstraint] = rule {
    // without name
    group(keyword("CREATE OR REPLACE CONSTRAINT IF NOT EXISTS") ~~ NodePropertyExistenceConstraintSyntax) ~~>> (ast.CreateNodePropertyExistenceConstraint(_, _, _, None, IfExistsInvalidSyntax)) |
    group(keyword("CREATE OR REPLACE CONSTRAINT") ~~ NodePropertyExistenceConstraintSyntax) ~~>> (ast.CreateNodePropertyExistenceConstraint(_, _, _, None, IfExistsReplace)) |
    group(keyword("CREATE CONSTRAINT IF NOT EXISTS") ~~ NodePropertyExistenceConstraintSyntax) ~~>> (ast.CreateNodePropertyExistenceConstraint(_, _, _, None, IfExistsDoNothing)) |
    group(keyword("CREATE CONSTRAINT") ~~ NodePropertyExistenceConstraintSyntax) ~~>> (ast.CreateNodePropertyExistenceConstraint(_, _, _, None, IfExistsThrowError)) |
    // with name
    group(keyword("CREATE OR REPLACE CONSTRAINT") ~~ SymbolicNameString ~~ keyword("IF NOT EXISTS") ~~ NodePropertyExistenceConstraintSyntax) ~~>>
      ((name, variable, labelName, property) => ast.CreateNodePropertyExistenceConstraint(variable, labelName, property, Some(name), IfExistsInvalidSyntax)) |
    group(keyword("CREATE OR REPLACE CONSTRAINT") ~~ SymbolicNameString ~~ NodePropertyExistenceConstraintSyntax) ~~>>
      ((name, variable, labelName, property) => ast.CreateNodePropertyExistenceConstraint(variable, labelName, property, Some(name), IfExistsReplace)) |
    group(keyword("CREATE CONSTRAINT") ~~ SymbolicNameString ~~ keyword("IF NOT EXISTS") ~~ NodePropertyExistenceConstraintSyntax) ~~>>
      ((name, variable, labelName, property) => ast.CreateNodePropertyExistenceConstraint(variable, labelName, property, Some(name), IfExistsDoNothing)) |
    group(keyword("CREATE CONSTRAINT") ~~ SymbolicNameString ~~ NodePropertyExistenceConstraintSyntax) ~~>>
      ((name, variable, labelName, property) => ast.CreateNodePropertyExistenceConstraint(variable, labelName, property, Some(name), IfExistsThrowError))
  }

  def CreateRelationshipPropertyExistenceConstraint: Rule1[ast.CreateRelationshipPropertyExistenceConstraint] = rule {
    // without name
    group(keyword("CREATE OR REPLACE CONSTRAINT IF NOT EXISTS") ~~ RelationshipPropertyExistenceConstraintSyntax) ~~>> (ast.CreateRelationshipPropertyExistenceConstraint(_, _, _, None, IfExistsInvalidSyntax)) |
    group(keyword("CREATE OR REPLACE CONSTRAINT") ~~ RelationshipPropertyExistenceConstraintSyntax) ~~>> (ast.CreateRelationshipPropertyExistenceConstraint(_, _, _, None, IfExistsReplace)) |
    group(keyword("CREATE CONSTRAINT IF NOT EXISTS") ~~ RelationshipPropertyExistenceConstraintSyntax) ~~>> (ast.CreateRelationshipPropertyExistenceConstraint(_, _, _, None, IfExistsDoNothing)) |
    group(keyword("CREATE CONSTRAINT") ~~ RelationshipPropertyExistenceConstraintSyntax) ~~>> (ast.CreateRelationshipPropertyExistenceConstraint(_, _, _, None, IfExistsThrowError)) |
    // with name
    group(keyword("CREATE OR REPLACE CONSTRAINT") ~~ SymbolicNameString ~~ keyword("IF NOT EXISTS") ~~ RelationshipPropertyExistenceConstraintSyntax) ~~>>
      ((name, variable, relTypeName, property) => ast.CreateRelationshipPropertyExistenceConstraint(variable, relTypeName, property, Some(name), IfExistsInvalidSyntax)) |
    group(keyword("CREATE OR REPLACE CONSTRAINT") ~~ SymbolicNameString ~~ RelationshipPropertyExistenceConstraintSyntax) ~~>>
      ((name, variable, relTypeName, property) => ast.CreateRelationshipPropertyExistenceConstraint(variable, relTypeName, property, Some(name), IfExistsReplace)) |
    group(keyword("CREATE CONSTRAINT") ~~ SymbolicNameString ~~ keyword("IF NOT EXISTS") ~~ RelationshipPropertyExistenceConstraintSyntax) ~~>>
      ((name, variable, relTypeName, property) => ast.CreateRelationshipPropertyExistenceConstraint(variable, relTypeName, property, Some(name), IfExistsDoNothing)) |
    group(keyword("CREATE CONSTRAINT") ~~ SymbolicNameString ~~ RelationshipPropertyExistenceConstraintSyntax) ~~>>
      ((name, variable, relTypeName, property) => ast.CreateRelationshipPropertyExistenceConstraint(variable, relTypeName, property, Some(name), IfExistsThrowError))
  }

  def DropUniqueConstraint: Rule1[ast.DropUniquePropertyConstraint] = rule {
    group(keyword("DROP CONSTRAINT") ~~ UniqueConstraintSyntax) ~~>>
      ((variable, label, property) => ast.DropUniquePropertyConstraint(variable, label, Seq(property)))
  }

  def DropUniqueCompositeConstraint: Rule1[ast.DropUniquePropertyConstraint] = rule {
    group(keyword("DROP CONSTRAINT") ~~ UniqueCompositeConstraintSyntax) ~~>> (ast.DropUniquePropertyConstraint(_, _, _))
  }

  def DropNodeKeyConstraint: Rule1[ast.DropNodeKeyConstraint] = rule {
    group(keyword("DROP CONSTRAINT") ~~ NodeKeyConstraintSyntax) ~~>> (ast.DropNodeKeyConstraint(_, _, _))
  }

  def DropNodePropertyExistenceConstraint: Rule1[ast.DropNodePropertyExistenceConstraint] = rule {
    group(keyword("DROP CONSTRAINT") ~~ NodePropertyExistenceConstraintSyntax) ~~>> (ast.DropNodePropertyExistenceConstraint(_, _, _))
  }

  def DropRelationshipPropertyExistenceConstraint: Rule1[ast.DropRelationshipPropertyExistenceConstraint] = rule {
    group(keyword("DROP CONSTRAINT") ~~ RelationshipPropertyExistenceConstraintSyntax) ~~>> (ast.DropRelationshipPropertyExistenceConstraint(_, _, _))
  }

  def DropConstraintOnName: Rule1[ast.DropConstraintOnName] = rule {
    group(keyword("DROP CONSTRAINT") ~~ SymbolicNameString ~~ keyword("IF EXISTS")) ~~>> (ast.DropConstraintOnName(_, ifExists = true)) |
    group(keyword("DROP CONSTRAINT") ~~ SymbolicNameString) ~~>> (ast.DropConstraintOnName(_, ifExists = false))
  }

  private def NodeKeyConstraintSyntax: Rule3[Variable, LabelName, Seq[Property]] = keyword("ON") ~~ "(" ~~ Variable ~~ NodeLabel ~~ ")" ~~
    keyword("ASSERT") ~~ "(" ~~ VariablePropertyExpressions ~~ ")" ~~ keyword("IS NODE KEY")

  private def UniqueConstraintSyntax: Rule3[Variable, LabelName, Property] = keyword("ON") ~~ "(" ~~ Variable ~~ NodeLabel ~~ ")" ~~
    keyword("ASSERT") ~~ VariablePropertyExpression ~~ keyword("IS UNIQUE")

  private def UniqueCompositeConstraintSyntax: Rule3[Variable, LabelName, Seq[Property]] = keyword("ON") ~~ "(" ~~ Variable ~~ NodeLabel ~~ ")" ~~
    keyword("ASSERT") ~~ "(" ~~ VariablePropertyExpressions ~~ ")" ~~ keyword("IS UNIQUE")

  private def NodePropertyExistenceConstraintSyntax = keyword("ON") ~~ "(" ~~ Variable ~~ NodeLabel ~~ ")" ~~
    keyword("ASSERT EXISTS") ~~ "(" ~~ VariablePropertyExpression ~~ ")"

  private def RelationshipPropertyExistenceConstraintSyntax = keyword("ON") ~~ RelationshipPatternSyntax ~~
    keyword("ASSERT EXISTS") ~~ "(" ~~ VariablePropertyExpression ~~ ")"

  private def RelationshipPatternSyntax = rule(
    ("()-[" ~~ Variable~~ RelType ~~ "]-()")
      | ("()-[" ~~ Variable~~ RelType ~~ "]->()")
      | ("()<-[" ~~ Variable~~ RelType ~~ "]-()")
  )
}
