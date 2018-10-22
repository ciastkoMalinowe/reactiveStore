package actors

import scala.concurrent.duration._
import akka.actor.{Actor, ActorRef, Timers}
import actors.Cart

case object Checkout {
  sealed trait Command
  case class Start(cart: ActorRef) extends Command
  case class Cancel(id: String) extends Command
  case class SelectDelivery(delivery: String) extends Command
  case class SelectPayment(payment: String) extends Command
  case class ReceivePayment() extends Command
  sealed trait Event
  case class DeliverySelected(delivery: String) extends Event
  case class PaymentSelected(payment: String) extends Event
  case class PaymentReceived() extends Event

  private case class EntryAction()
  private case class ExitAction()

  private case object TimerKey
  private case object Timeout
}

class Checkout() extends Actor with Timers{

  def receive = idle

  def idle: Receive = {
    case Checkout.Start(cart) => {
      changeState(selectingDelivery(cart))
    }
  }

  def selectingDelivery(cart: ActorRef): Receive = {
    case Checkout.EntryAction => {
      timers.startSingleTimer(Checkout.TimerKey, Checkout.Timeout, 5.minutes)
    }
    case Checkout.SelectDelivery => {
      changeState(selectingPaymentMethod(cart))
    }
    case Checkout.Timeout => {
      changeState(cancelled(cart))
    }
    case Checkout.Cancel => {
      changeState(cancelled(cart))
    }
  }

  def selectingPaymentMethod(cart: ActorRef): Receive = {
    case Checkout.PaymentSelected => {
      changeState(processingPayment(cart))
    }
    case Checkout.Timeout => {
      changeState(cancelled(cart))
    }
    case Checkout.Cancel => {
      changeState(cancelled(cart))
    }
    case Checkout.ExitAction => {
      timers.cancel(Checkout.TimerKey)
    }
  }

  def processingPayment(cart: ActorRef): Receive = {
    case Checkout.EntryAction => {
      timers.startSingleTimer(Checkout.TimerKey, Checkout.Timeout, 5.minutes)
    }
    case Checkout.PaymentReceived => {
      changeState(closed(cart))
    }
    case Checkout.Cancel => {
      changeState(cancelled(cart))
    }
    case Checkout.Timeout => {
      changeState(cancelled(cart))
    }
    case Checkout.ExitAction => {
      timers.cancel(Checkout.TimerKey)
    }
  }

  def closed(cart: ActorRef): Receive = {
    case Checkout.EntryAction => {
      context stop self
    }
  }

  def cancelled(cart: ActorRef): Receive = {
    case Checkout.EntryAction => {
      cart ! Cart.CheckoutCancelled
    }
  }
  def changeState(state: Receive): Unit ={
    self ! Checkout.ExitAction()
    context.become(state)
    self ! Checkout.EntryAction()
  }
}