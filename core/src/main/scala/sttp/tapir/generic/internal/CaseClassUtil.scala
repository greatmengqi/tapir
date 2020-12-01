package sttp.tapir.generic.internal

import scala.reflect.macros.blackbox

private[generic] class CaseClassUtil[C <: blackbox.Context, T: C#WeakTypeTag](val c: C, name: String) {
  import c.universe._

  val t: Type = weakTypeOf[T]
  if (!t.typeSymbol.isClass || !t.typeSymbol.asClass.isCaseClass) {
    c.error(c.enclosingPosition, s"${name.capitalize} can only be generated for a case class, but got: $t.")
  }

  lazy val fields: List[Symbol] = t.decls
    .collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }
    .get
    .paramLists
    .head

  private lazy val companion: Ident = Ident(TermName(t.typeSymbol.name.decodedName.toString))

  lazy val instanceFromValues: Tree = if (fields.size == 1) {
    q"$companion.apply(values.head.asInstanceOf[${fields.head.typeSignature}])"
  } else {
    q"$companion.tupled.asInstanceOf[Any => $t].apply(sttp.tapir.internal.SeqToParams(values))"
  }

  lazy val schema: Tree = c.typecheck(q"implicitly[sttp.tapir.Schema[$t]]")

  lazy val classSymbol = t.typeSymbol.asClass

  def annotated(field: Symbol, annotationType: c.Type): Boolean =
    field.annotations.exists(_.tree.tpe <:< annotationType)

  def extractArgFromAnnotation(field: Symbol, annotationType: c.Type): Option[String] = {
    // https://stackoverflow.com/questions/20908671/scala-macros-how-to-read-an-annotation-object
    field.annotations.collectFirst {
      case a if a.tree.tpe <:< annotationType =>
        a.tree.children.tail match {
          case List(Literal(Constant(str: String))) => str
        }
    }
  }

  def extractOptArgFromAnnotation(field: Symbol, annotationType: c.Type): Option[Option[String]] =
    field.annotations.collectFirst {
      case a if a.tree.tpe <:< annotationType =>
        a.tree.children.tail match {
          case List(Select(_, name @ TermName(_)))
            if name.decodedName.toString.startsWith("<init>$default") =>
            None
          case List(Literal(Constant(str: String))) =>
            Some(str)
        }
    }
}
