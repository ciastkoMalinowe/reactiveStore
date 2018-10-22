package actors

import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.Timers
import actors.Cart

case object Checkout {
  sealed trait Command
  case class Start() extends Command
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

class Checkout(cart: ActorRef) extends Actor with Timers{

  def receive = idle

  def idle: Receive = {
    case Checkout.Start => {
      changeState(selectingDelivery)
    }
  }

  def selectingDelivery: Receive = {
    case Checkout.EntryAction => {
      timers.startSingleTimer(Checkout.TimerKey, Checkout.Timeout, 5.minutes)
    }
    case Checkout.SelectDelivery => {
      changeState(selectingPaymentMethod)
    }
    case Checkout.Timeout => {
      changeState(cancelled)
    }
    case Checkout.Cancel => {
      changeState(cancelled)
    }
  }

  def selectingPaymentMethod: Receive = {
    case Checkout.PaymentSelected => {
      changeState(processingPayment)
    }
    case Checkout.Timeout => {
      changeState(cancelled)
    }
    case Checkout.Cancel => {
      changeState(cancelled)
    }
    case Checkout.ExitAction => {
      timers.cancel(TimerKey)
    }
  }

  def processingPayment: Receive = {
    case Checkout.EntryAction => {
      timers.startSingleTimer(Checkout.TimerKey, Checkout.Timeout, 5.minutes)
    }
    case Checkout.PaymentReceived => {
      changeState(closed)
    }
    case Checkout.Cancel => {
      changeState(cancelled)
    }
    case Checkout.Timeout => {
      changeState(cancelled)
    }
    case Checkout.ExitAction => {
      timers.cancel(timerKey)
    }
  }

  def closed: Receive = {
    case Checkout.EntryAction => {
      context stop self
    }
  }

  def cancelled: Receive = {
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