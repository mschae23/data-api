package de.martenschaefer.data.serialization.codec

import scala.collection.immutable.ListMap
import de.martenschaefer.data.Result
import de.martenschaefer.data.serialization.Element._
import de.martenschaefer.data.serialization.RecordParseError._
import de.martenschaefer.data.serialization.{ Codec, Element, ElementError, ElementNode, FieldCodec, RecordParseError }
import de.martenschaefer.data.util.DataResult._
import de.martenschaefer.data.util.Lifecycle

class RecordCodec[T](fields: List[FieldCodec[_, T]], creator: (FieldCodec[_, T] => _) ?=> T) extends Codec[T] {
    def encodeElement(value: T): Result[Element] =
        Success(fields.map(field => (field.fieldName, field.getField(value).flatMap(t => field.encodeElement(t))))).flatMap(fields =>
            fields.foldLeft((List[ElementError](), Lifecycle.Stable))((acc, fieldTuple) => fieldTuple._2 match {
                case Failure(errors, lifecycle) => (acc._1 ::: errors.map(_.withPrependedPath(fieldTuple._1)),
                    acc._2 + lifecycle)
                case Success(result, lifecycle) => (acc._1, acc._2 + lifecycle)
            }) match {
                case acc if !acc._1.isEmpty => Failure(acc._1, acc._2)
                case acc => Success(fields.map((fieldName, result) => (fieldName, result.getRight)), acc._2)
            }
        ).map(fields => ObjectElement(ListMap.from(fields)))

    def decodeElement(element: Element): Result[T] =
        element match {
            case Element.ObjectElement(map) => {
                var fieldMap = Map[FieldCodec[_, T], Any]()
                var errors = List[ElementError]()
                var lifecycle = Lifecycle.Stable

                for (fieldCodec <- fields) {
                    val field = map.get(fieldCodec.fieldName).getOrElse(Element.None)
                    val decoded = fieldCodec.decodeElement(field) match {
                        case Failure(_, l) if field == Element.None => Failure(List(MissingKey(element, List.empty)), l)
                        case result => result
                    }

                    decoded match {
                        case Success(value, l) => fieldMap = fieldMap.updated(fieldCodec, value)
                            lifecycle += l
                        case Failure(fieldErrors, l) => errors = errors ::: fieldErrors.map(_
                          .withPrependedPath(fieldCodec.fieldName))
                            lifecycle += l
                    }
                }

                if (errors.isEmpty)
                    Success(creator(using fieldCodec => fieldMap(fieldCodec)), lifecycle)
                else
                    Failure(errors, lifecycle)
            }

            case _ => Failure(List(RecordParseError.NotAnObject(element, List.empty)), this.lifecycle)
        }

    override val lifecycle: Lifecycle =
        this.fields.foldLeft(Lifecycle.Stable)((lifecycle, field) => lifecycle + field.lifecycle)
}
