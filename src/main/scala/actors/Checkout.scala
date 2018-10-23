package actors

import scala.concurrent.duration._
import akka.actor.{Actor, ActorRef, Timers}
import actors.Cart
import akka.event.LoggingReceive

case object Checkout {
  sealed trait Command

  case object Cancel extends Command
  case object Start extends Command

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

class Checkout extends Actor with Timers{

  val selectExpirationTime: FiniteDuration = 30.seconds
  val payExpirationTime: FiniteDuration = 30.seconds
  var sDelivery: Checkout.Delivery.DeliveryType = null
  var sPayment: Checkout.PaymentMethod.PaymentType = null
  def receive = LoggingReceive{idle}

  def idle: Receive = {
    case Checkout.Start => {
      context become LoggingReceive{selectingDelivery(sender)}
      onEntrySelectingDelivery()
    }
  }

  def selectingDelivery(cart: ActorRef): Receive = {

    case Checkout.SelectDelivery(delivery) => {
      sDelivery = delivery
      context become LoggingReceive{selectingPaymentMethod(cart)}
    }
    case Checkout.Timeout => {
      cancel(cart)
    }
    case Checkout.Cancel => {
      cancel(cart)
    }
  }

  def selectingPaymentMethod(cart: ActorRef): Receive = {
    case Checkout.SelectPayment(payment) => {
      sPayment = payment
      onEntryProcessingPayment()
      context become LoggingReceive{processingPayment(cart)}
      onExitSelectingPaymentMethod()
    }
    case Checkout.Timeout => {
      onExitSelectingPaymentMethod()
      cancel(cart)
    }
    case Checkout.Cancel => {
      onExitSelectingPaymentMethod()
      cancel(cart)
    }
  }

  def processingPayment(cart: ActorRef): Receive = {

    case Checkout.ReceivePayment => {
      onExitProcessingPayment()
      cart ! Cart.CheckoutClosed(sDelivery, sPayment)
      sender ! Cart.CheckoutClosed(sDelivery, sPayment)
      context stop self
    }
    case Checkout.Cancel => {
      onExitProcessingPayment()
      cancel(cart)
    }
    case Checkout.Timeout => {
      onExitProcessingPayment()
      cancel(cart)
    }
  }

  def onEntrySelectingDelivery(): Unit = {
    timers.startSingleTimer(Checkout.CheckoutTimer, Checkout.Timeout, selectExpirationTime)
  }

  def onExitSelectingPaymentMethod(): Unit = {
    timers.cancel(Checkout.CheckoutTimer)
  }

  def onEntryProcessingPayment(): Unit = {
    timers.startSingleTimer(Checkout.PaymentTimer, Checkout.Timeout, payExpirationTime)
  }

  def onExitProcessingPayment(): Unit = {
    timers.cancel(Checkout.PaymentTimer)
  }

  def cancel(cart: ActorRef): Unit = {
    cart ! Cart.CheckoutCancelled
    //context stop self
  }

}