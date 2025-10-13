/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n.utils

import scala.quoted.*

import magnolia1.Transl

// use definition form fork of "magnolia" lib where "Transl" is defined as :
// case class Transl(key: List[String]) extends StaticAnnotation

case class TranslAnnotations(
    classNameOrig: String,
    classNameTranslKey: Option[List[String]],
    paramsTranslKey: Seq[(String, Option[List[String]])]
)

case class TranslatedFieldsWithValues(
    classNameOrig: String,
    classNameTransl: Option[String],
    paramsTransl: Map[String, Option[String]]
)

object macros:

    inline def getTranslatedFieldsWithValues[T, I](using i: I): TranslatedFieldsWithValues = 
        ${ getTranslatedFieldsWithValuesImpl[T, I]('i) }

    private def getTranslatedFieldsWithValuesImpl[T: Type, I18nType: Type](i18nData: Expr[I18nType])(using q: Quotes): Expr[TranslatedFieldsWithValues] = 
        
        def getTranslFromPathExpr(pathExpr: Expr[List[String]]): Expr[Option[String]] =
            val constantPath = tPathImpl(pathExpr)
            accessPathImpl[I18nType, String](i18nData, constantPath)

        // desctructure and match to access className, classNameTranslKey, etc... at compile-time
        getTranslAnnotImpl[T] match
            case '{ 
                TranslAnnotations(
                    $classNamePath, 
                    $classNameTranslKeyOpt, 
                    $paramsTranslSeq
                ) 
            } =>

                val classNameTransl = classNameTranslKeyOpt match
                    case '{ Some($classNameTranslKey: List[String]) } => 
                        getTranslFromPathExpr(classNameTranslKey)
                    case '{ None } => 
                        '{ None }

                val paramsTranslated = paramsTranslSeq match
                    case Varargs(paramPairs) =>
                        paramPairs.toList.map: pair =>
                            pair match
                                case '{ ($fieldName: String, $pathOpt: Option[List[String]]) } =>
                                    val translOpt = pathOpt match
                                        case '{ Some[List[String]]($path) } => getTranslFromPathExpr(path)
                                        case '{ None } => '{ None }
                                    Expr.ofTuple((fieldName, translOpt))
                '{
                    TranslatedFieldsWithValues(
                        classNameOrig   = $classNamePath,
                        classNameTransl = $classNameTransl,
                        paramsTransl    = ${Expr.ofList(paramsTranslated)}.toMap
                    )
                }


    inline def accessPath[T, R](inline obj: T, inline path: List[String]): Option[R] = 
        ${ accessPathImpl[T, R]('obj, 'path) }

    private def accessPathImpl[T: Type, R: Type](
        obj: Expr[T], 
        path: Expr[List[String]]
    )(using Quotes): Expr[Option[R]] =
        import quotes.reflect.*
        // Generate direct field access code
        path.value.getOrElse(report.errorAndAbort(s"Path must be a compile-time constant, but got :\n${path.asTerm.show(using Printer.TreeCode)}")) match
            case fieldPath =>
                try
                    val term = fieldPath.foldLeft[Term](obj.asTerm): (curr, field) =>
                        Select.unique(curr, field)
                    '{ Some(${term.asExprOf[R]}) }
                catch
                    case _: Exception => '{ None }

    inline def getTranslAnnot[T]: TranslAnnotations = 
        ${ getTranslAnnotImpl[T] } 

    private def getTranslAnnotImpl[T: Type](using Quotes): Expr[TranslAnnotations] = 
        import quotes.reflect.*
        val className = TypeRepr.of[T].typeSymbol.name
        val onCaseClass = getTranslAnnot_ForCaseClassImpl[T]
        val onFields    = getTranslAnnot_onFields_ForImpl[T]
        '{
            TranslAnnotations(
                ${Expr(className)},
                $onCaseClass,
                $onFields
            )
        }

    // find and return translation path, if it exist, for class name
    // 
    // @Transl(I(_.klass.name)) case class MyClass(fooBar: FooBar)
    // returns List("klass", "name")
    inline def getTranslAnnot_ForCaseClass[T]: Option[List[String]] = ${ getTranslAnnot_ForCaseClassImpl[T] } 

    private def getTranslAnnot_ForCaseClassImpl[T: Type](using Quotes): Expr[Option[List[String]]] =
        import quotes.reflect.*
        val annot = TypeRepr.of[Transl]
        TypeRepr
            .of[T]
            .typeSymbol
            .annotations
            .find:
                case term if term.tpe =:= annot => true
                case _ => false
            .map:
                case term =>
                    term.asExprOf[Transl] match
                        // case '{ Transl($key) } => '{ Some($key) }
                        // case '{ $xPath: List[String] } => '{ Some($xPath) }
                        case other => 
                            val xPath = tPathImpl(other)
                            '{ Some($xPath) }
            .getOrElse(Expr(None))

    // find and return translation path, if it exists, for each case class field
    // returns fieldName -> Some(List("foo", "bar")) where List("foo", "bar") is the path to i18n translation
    // case class MyClass(@Transl(I(_.foo.bar)) fooBar: FooBar)
    // returns Seq(fooBar -> List("foo", "bar"))

    inline def getTranslAnnot_onFields_For[T]: Seq[(String, Option[List[String]])] = ${
        getTranslAnnot_onFields_ForImpl[T]
    }

    private def getTranslAnnot_onFields_ForImpl[T: Type](using Quotes): Expr[Seq[(String, Option[List[String]])]] =
        import quotes.reflect.*
        val annot = TypeRepr.of[Transl].typeSymbol
        val coll: Seq[Expr[(String, Option[List[String]])]] = TypeRepr
            .of[T]
            .typeSymbol
            .primaryConstructor
            .paramSymss
            .flatten
            .map:
                case sym if sym.hasAnnotation(annot) =>
                    val fieldNameExpr = Expr(sym.name)
                    val annotTerm = sym.getAnnotation(annot).get
                    // report.warning(annotTerm.show(using Printer.TreeShortCode))
                    val annotExpr = annotTerm.asExprOf[Transl]
                    val annotSomeExprKey = annotExpr match
                        case other => 
                            val xPath = tPathImpl(other)
                            '{ Some($xPath) }
                    Expr.ofTuple(fieldNameExpr, annotSomeExprKey)
                case sym =>
                    val fieldNameExpr = Expr(sym.name)
                    Expr.ofTuple(fieldNameExpr, '{None})
        Expr.ofSeq(coll)
    end getTranslAnnot_onFields_ForImpl

    inline def debugAST[T](x: T): Unit = ${ debugASTImpl('x) }

    def debugASTImpl[T: Type](expr: Expr[T])(using Quotes): Expr[Unit] = 
        import quotes.reflect.*
        // val out = s"Debug AST: ${expr.asTerm.show(using Printer.TreeStructure)}"
        expr.asTerm match
            case Inlined(_, _, body) =>
                report.errorAndAbort(s"Body AST: ${body.underlying.show(using Printer.TreeStructure)}")
                '{()}
            case _ =>
                val out = s"Full AST: ${expr.asTerm.show(using Printer.TreeStructure)}"
                report.error(out)
                '{()}

    // given ```path = List("foo", "bar")``` generates ```x.foo.bar``` at compile time
    inline def applyPathOn[T](x: T, inline path: List[String]): String = 
        ${ applyPathOnImpl('x, 'path) }

    private def applyPathOnImpl[T: Type](
        expr: Expr[T], 
        pathExpr: Expr[List[String]]
    )(using Quotes): Expr[String] =
        import quotes.reflect.*
        val identTerm = expr.asTerm match
            case Inlined(_, _, i @ Ident(_)) => 
                i
            case Inlined(_, _, unexp) => 
                report.errorAndAbort(s"unexpected AST : ${unexp.show(using Printer.TreeStructure)}")
                unexp
        val finalTerm = pathExpr match
            case '{ $keys: List[String] } =>
                keys.valueOrAbort.foldLeft(identTerm): (term, key) =>
                    Select.unique(term, key)
        finalTerm.asExprOf[String]

    // translation path
    // given an anonymous function of form ```_.foo.bar``` (or typed equivalent) returns ```List("foo", "bar")``` at compile time

    inline def tPath[A](inline lambda: A => String): List[String] = ${ tPathImpl('lambda) }

    private def tPathImpl[T: Type](expr: Expr[T])(using Quotes): Expr[List[String]] =
        import quotes.reflect.*

        def extractSelectsFromDefDef(tree: Term): List[String] = tree match
            case Typed(Inlined(_, _, body), Inferred()) =>
                extractSelectsFromDefDef(body)
            case Inlined(_, _, body) =>
                extractSelectsFromDefDef(body)
            case Block(DefDef(_, _, _, Some(x)) :: Nil, _) =>
                extractSelectsFromDefDef(x)
            case reap @ Repeated(xs, Inferred()) =>
                xs.map:
                    case Literal(StringConstant(s)) => s
                    case x =>
                        report.errorAndAbort(s"Unexpected Term ${x.show} in 'Repeated' structure: ${reap.show}", expr)
            case Select(s @ Select(_, _), name) =>
                extractSelectsFromDefDef(s) :+ name
            case Select(Ident(_), name) => 
                List(name)
            case Select(Typed(_, _), name) =>
                List(name)
            case Select(qual, name) =>
                extractSelectsFromDefDef(qual) :+ name
            case _ @ Ident(_) => 
                extractSelectsFromDefDef(tree.underlying)
            case _ => 
                // report.warning(s"AST:\n${rem.show(using Printer.TreeStructure)}")
                report.errorAndAbort(s"Unexpected tree structure (selectsFromDefDef): ${tree.show}", expr)
                Nil

        def extractDefDef(tree: Term): List[String] = 
            tree match
                case Apply(Ident(_), List(body)) =>
                    extractDefDef(body)
                case Apply(Select(New(TypeIdent(_)), "<init>"), List(body)) =>
                    extractDefDef(body)
                case Select(Inlined(_, _, body), _) =>
                    extractDefDef(body)
                case Inlined(_, _, body) =>
                    extractDefDef(body)
                case _ @ Ident(_) =>
                    extractDefDef(tree.underlying)
                case Apply(TypeApply(Select(Ident("List"), "apply"), List(Inferred())), List(body)) =>
                    extractSelectsFromDefDef(body)
                case _ @ Block((_ @ DefDef(_, _, _, Some(x))) :: Nil, _) =>
                    extractSelectsFromDefDef(x)
                case rem => 
                    // report.warning(s"AST:\n${rem.show(using Printer.TreeStructure)}")
                    report.errorAndAbort(s"Unexpected tree structure (defdef): ${rem.show(using Printer.TreeStructure)}", expr)
                    Nil
        expr match
            case '{ $xs: List[String] } => 
                // report.warning(s"returning list of string : ${expr.asTerm.show(using Printer.TreeShortCode)}")
                xs
            case expr =>
                val path = extractDefDef(expr.asTerm)
                Expr(path)
    end tPathImpl