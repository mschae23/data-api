package de.martenschaefer.data.util

enum DataResult[+L, +R](val lifecycle: Lifecycle) {
    case Failure[+L, +R](val value: L, override val lifecycle: Lifecycle = Lifecycle.Stable) extends DataResult[L, R](lifecycle)
    case Success[+L, +R](val value: R, override val lifecycle: Lifecycle = Lifecycle.Stable) extends DataResult[L, R](lifecycle)

    def map[B](f: R => B): DataResult[L, B] = this match {
        case Success(value, lifecycle) => Success(f(value), lifecycle)
        case _ => this.asInstanceOf[DataResult[L, B]]
    }

    def mapLeft[B](f: L => B): DataResult[B, R] = this match {
        case Failure(value, lifecycle) => Failure(f(value), lifecycle)
        case _ => this.asInstanceOf[DataResult[B, R]]
    }

    def flatMap[L1 >: L, R1](f: R => DataResult[L1, R1]): DataResult[L1, R1] = this match {
        case Success(value, _) => f(value)
        case _ => this.asInstanceOf[DataResult[L1, R1]]
    }

    def flatMapLeft[R1 >: R, L1](f: L => DataResult[L1, R1]): DataResult[L1, R1] = this match {
        case Failure(value, _) => f(value)
        case _ => this.asInstanceOf[DataResult[L1, R1]]
    }

    def mapBoth[L1, R1](l: L => L1)(r: R => R1): DataResult[L1, R1] = this match {
        case Failure(value, lifecycle) => Failure(l(value), lifecycle)
        case Success(value, lifecycle) => Success(r(value), lifecycle)
    }

    def flatMapBoth[L1, R1](l: L => DataResult[L1, R1])(r: R => DataResult[L1, R1]): DataResult[L1, R1] = this match {
        case Failure(value, _) => l(value)
        case Success(value, _) => r(value)
    }

    def flatMapWithLifecycle[L1 >: L, R1](f: (R, Lifecycle) => DataResult[L1, R1]): DataResult[L1, R1] = this match {
        case Success(value, l) => f(value, l)
        case _ => this.asInstanceOf[DataResult[L1, R1]]
    }

    def flatMapLeftWithLifecycle[R1 >: R, L1](f: (L, Lifecycle) => DataResult[L1, R1]): DataResult[L1, R1] = this match {
        case Failure(value, l) => f(value, l)
        case _ => this.asInstanceOf[DataResult[L1, R1]]
    }

    @deprecated("This will be removed, as it is just a replacement for a match expression", "3.0.0")
    def flatMapBothWithLifecycle[L1, R1](l: (L, Lifecycle) => DataResult[L1, R1])(r: (R, Lifecycle) => DataResult[L1, R1]): DataResult[L1, R1] = this match {
        case Failure(value, lifecycle) => l(value, lifecycle)
        case Success(value, lifecycle) => r(value, lifecycle)
    }

    def get[T](l: L => T)(r: R => T): T =
        this match {
            case Failure(value, _) => l(value)
            case Success(value, _) => r(value)
        }

    def getOrElse[L1 >: L, R1 >: R](default: => DataResult[L1, R1]): DataResult[L1, R1] = this.get(_ => default)(_ => this)

    def orElse[R1 >: R](alternative: => R1): DataResult[L, R1] = this match {
        case Failure(_, lifecycle) => Success(alternative, lifecycle)
        case _ => this
    }

    @deprecated
    def flatOrElse[L1 >: L, R1](alternative: => DataResult[L1, R1]): DataResult[L1, R1] = this match {
        case Failure(_, _) => alternative
        case _ => this.asInstanceOf[DataResult[L1, R1]]
    }

    /**
     * Creates a new {@code DataResult} that is the same as {@code this}, but has a different {@link Lifecycle}.
     *
     * @param lifecycle The {@code Lifecycle} used for the new {@code DataResult}.
     * @return The {@code DataResult} with the new {@code Lifecycle}.
     */
    def withLifecycle(lifecycle: Lifecycle): DataResult[L, R] = this match {
        case Success(value, _) => Success(value, lifecycle)
        case Failure(value, _) => Failure(value, lifecycle)
    }

    /**
     * Creates a new {@code DataResult} that is the same as {@code this},
     * but has {@code Stable} as its {@link Lifecycle}.
     *
     * @return The new {@code DataResult}.
     */
    def stable: DataResult[L, R] = this.withLifecycle(Lifecycle.Stable)

    /**
     * Creates a new {@code DataResult} that is the same as {@code this},
     * but has {@code Experimental} as its {@link Lifecycle}.
     *
     * @return The new {@code DataResult}.
     */
    def experimental: DataResult[L, R] = this.withLifecycle(Lifecycle.Experimental)

    /**
     * Creates a new {@code DataResult} that is the same as {@code this},
     * but has {@code Deprecated} as its {@link Lifecycle}.
     *
     * @param since Since when this has been deprecated.
     * @return The new {@code DataResult}.
     */
    def deprecated(since: Int): DataResult[L, R] = this.withLifecycle(Lifecycle.Deprecated(since))

    /**
     * Creates a new {@code DataResult} that has another {@link Lifecycle} added to to it.
     *
     * @param lifecycle The {@code Lifecycle} that is added to this one.
     * @return The {@code DataResult} with the new {@code Lifecycle}.
     */
    def addLifecycle(lifecycle: Lifecycle): DataResult[L, R] = this match {
        case Success(value, l) => Success(value, l + lifecycle)
        case Failure(value, l) => Failure(value, l + lifecycle)
    }

    /**
     * @throws IllegalStateException if {@code this} is not {@code Failure}
     */
    def getLeft: L = this match {
        case Failure(value, _) => value
        case _ => throw new IllegalStateException
    }

    /**
     * @throws IllegalStateException if {@code this} is not {@code Success}
     */
    def getRight: R = this match {
        case Success(value, _) => value
        case _ => throw new IllegalStateException
    }

    def isFailure: Boolean = this match {
        case Failure(_, _) => true
        case _ => false
    }

    @deprecated
    def isLeft: Boolean = this.isFailure

    def isSuccess: Boolean = this match {
        case Success(_, _) => true
        case _ => false
    }

    @deprecated
    def isRight: Boolean = this.isSuccess
}

import scala.annotation.tailrec
import cats.Monad

object DataResult {
    given[L]: Monad[[R] =>> DataResult[L, R]] with {
        override def pure[R](value: R): DataResult[L, R] = Success(value)

        override def flatMap[R, R1](either: DataResult[L, R])(f: R => DataResult[L, R1]): DataResult[L, R1] = either.flatMap(f)

        override def tailRecM[A, B](a: A)(f: A => DataResult[L, Either[A, B]]): DataResult[L, B] = {
            @tailrec
            def loop(aa: A): DataResult[L, B] = f(a) match {
                case Success(either, lifecycle) => either match {
                    case Left(a1) => loop(a1)
                    case Right(b) => Success(b, lifecycle)
                }

                case Failure(value, lifecycle) => Failure(value, lifecycle)
            }

            loop(a)
        }
    }
}