package de.martenschaefer.serialization.codec

import de.martenschaefer.serialization.{ Codec, Decoded, Element, ElementError, ElementNode }
import de.martenschaefer.serialization.Element._
import de.martenschaefer.serialization.util.Either
import de.martenschaefer.serialization.util.Either._

class EitherCodec[L: Codec, R: Codec] extends Codec[Either[L, R]] {
    override def encodeElement(option: Either[L, R]): Element = option match {
        case Left(value) => Codec[L].encodeElement(value)
        case Right(value) => Codec[R].encodeElement(value)
    }

    override def decodeElement(element: Element): Decoded[Either[L, R]] =
        Codec[L].decodeElement(element) match {
            case Right(value) => Right(Left(value))
            case Left(errors) => Codec[R].decodeElement(element) match {
                case Right(value) => Right(Right(value))
                case Left(errors2) => Left(Vector(ElementError.Neither(element, List())))
            }
        }
}