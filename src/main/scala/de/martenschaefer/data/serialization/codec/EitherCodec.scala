package de.martenschaefer.data.serialization.codec

import de.martenschaefer.data.serialization.Element._
import de.martenschaefer.data.serialization.{ Codec, Element, ElementError, ElementNode, RecordParseError, Result }
import de.martenschaefer.data.util.Either._
import de.martenschaefer.data.util.{ Either, Lifecycle }

class EitherCodec[L: Codec, R: Codec](val errorMessage: String => String) extends Codec[Either[L, R]] {
    override def encodeElement(option: Either[L, R]): Result[Element] = option match {
        case Left(value) => Codec[L].encodeElement(value)
        case Right(value) => Codec[R].encodeElement(value)
    }

    override def decodeElement(element: Element): Result[Either[L, R]] =
        Codec[L].decodeElement(element) match {
            case Right(value) => Right(Left(value))
            case Left(errors) => Codec[R].decodeElement(element) match {
                case Right(value) => Right(Right(value))
                case Left(errors2) => Left(Vector(RecordParseError.EitherParseError(this.errorMessage, element, List())))
            }
        }

    override val lifecycle: Lifecycle = Codec[L].lifecycle + Codec[R].lifecycle
}
