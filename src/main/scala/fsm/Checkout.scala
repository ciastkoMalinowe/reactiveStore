package fsm

import scala.concurrent.duration._
import akka.actor.{ActorRef, FSM, Timers}
import akka.event.LoggingReceive
import fsm.Cart.CheckoutClosed
import fsm.Checkout.Delivery
import fsm.Checkout.PaymentMethod

case object Checkout {
  sealed trait Command
  case object Cancel extends Command

  case object Delivery {
    sealed trait DeliveryType
    case object None extends DeliveryType
    case object Courier extends DeliveryType
    case object Post extends DeliveryType
    case object Shop extends DeliveryType
  }
  case class SelectDelivery(delivery: Delivery.DeliveryType) extends Command

  case object PaymentMethod {
    sealed trait PaymentType
    case object None extends PaymentType
    case object Transfer extends PaymentType
    case object CreditCard extends PaymentType
    case object Cash extends PaymentType
  }
  case class SelectPayment(payment: PaymentMethod.PaymentType) extends Command

  case object ReceivePayment extends Command

  sealed trait Event
  case class DeliverySelected(delivery: Delivery.DeliveryType) extends Event
  case class PaymentSelected(payment: PaymentMethod.PaymentType) extends Event
  case object PaymentReceived extends Event

  private case object CheckoutTimer
  private case object PaymentTimer
  private case object Timeout
}

trait CheckoutState
case object DeliverySelection extends CheckoutState
case object PaymentSelection extends CheckoutState
case object Payment extends CheckoutState
case object Canceled extends CheckoutState
case object Closed extends CheckoutState

case class CheckoutDetails(delivery: Delivery.DeliveryType, payment: PaymentMethod.PaymentType)

class Checkout() extends FSM[CheckoutState, CheckoutDetails] with Timers{

  val selectExpirationTime: FiniteDuration = 30.seconds
  val payExpirationTime: FiniteDuration = 30.seconds
  val cart: ActorRef = context.parent

  startWith(DeliverySelection, CheckoutDetails(Delivery.None, PaymentMethod.None))

  when(DeliverySelection){

    case Event(Checkout.SelectDelivery(delivery), details) => {
      goto(PaymentSelection) using CheckoutDetails(delivery, details.payment)
    }
    case Event(Checkout.Timeout, details) => {
      goto(Canceled) using details
    }
    case Event(Checkout.Cancel, details) => {
      goto(Canceled) using details
    }
  }

  when(PaymentSelection){
    case Event(Checkout.SelectPayment(payment), details) => {
      goto(Payment) using CheckoutDetails(details.delivery, payment)
    }
    case Event(Checkout.Timeout, details) => {
      goto(Canceled) using details
    }
    case Event(Checkout.Cancel, details) => {
      goto(Canceled) using details
    }
  }

  when(Payment) {

    case Event(Checkout.ReceivePayment, details) => {
      goto(Closed) using details replying CheckoutClosed(details.delivery, details.payment)
    }
    case Event(Checkout.Cancel, details) => {
      goto(Canceled) using details
    }
    case Event(Checkout.Timeout, details) => {
      goto(Canceled) using details
    }
  }

  onTransition {
    case _ -> DeliverySelection => timers.startSingleTimer(Checkout.CheckoutTimer, Checkout.Timeout, selectExpirationTime)
    case PaymentSelection -> Payment => {
      timers.cancel(Checkout.CheckoutTimer)
      timers.startSingleTimer(Checkout.PaymentTimer, Checkout.Timeout, payExpirationTime)
    }
    case _ -> Canceled => {
      timers.cancel()
      cart ! Cart.CheckoutCancelled
      context stop self
    }
    case _ -> Closed => {
      context stop self
    }
  }
}
