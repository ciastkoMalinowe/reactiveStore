package actors

import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.Timers
import actors.Checkout

case object Cart {
  sealed trait Command
  case class AddItem(id: String) extends Command
  case class RemoveItem(id: String) extends Command
  case class StartCheckout() extends Command
  sealed trait Event
  case class ItemAdded(id: String) extends Event
  case class ItemRemoved(id: String) extends Event
  case class CheckoutStarted(checkout: ActorRef) extends Event
  case class CheckoutCancelled() extends Event

  private case class EntryAction()
  private case class ExitAction()

  private case object TimerKey
  private case object Timeout
}

class Cart extends Actor with Timers{

  var items = collection.mutable.Map()
  var itemCount = 0
  def receive = empty

  def empty: Receive = {
    case Cart.EntryAction => {
      emptyCart()
    }
    case Cart.AddItem(id) => {
      addItem(id)
      changeState(nonEmpty)
      sender ! Cart.ItemAdded()
    }
  }

  def nonEmpty(): Receive = {
    case Cart.EntryAction => {
      timers.startSingleTimer(Cart.TimerKey, Cart.Timeout, 5.minutes)
    }
    case Cart.AddItem(id) => {
      addItem(id)
      sender ! Cart.ItemAdded()
    }
    case Cart.RemoveItem(id) => {
      removeItem(id)
      if itemCount == 0 {
        changeState(empty)
      }
      sender ! Cart.ItemRemoved(id)
    }
    case Cart.StartCheckout() => {
      changeState(inCheckout)
    }
    case Cart.Timeout() => {
      changeState(empty)
    }
    case Cart.ExitAction() => {
      timers.cancel(Cart.TimerKey)
    }
  }

  def inCheckout(sender: ActorRef): Receive = {
    case Cart.EntryAction() => {
      val checkout = context.actorOf(Props[Checkout(self)], "checkout")
      checkout ! Checkout.Start()
      sender ! Cart.CheckoutStarted(checkout)
    }
    case Cart.CheckoutCancelled() => {
      changeState(nonEmpty())
    }
    case Cart.ExitAction() => {
      //remove checkout
    }
  }

  def changeState(state: Receive): Unit ={
    self ! Cart.ExitAction()
    context.become(state)
    self ! Cart.EntryAction()
  }

  def addItem(id: String): Unit ={
    items(id) = items.get(id) match {
      case None => 1
      case Some(i :Integer) => i + 1
    }
    itemCount += 1
  }

  def removeItem(id: String): Unit ={
    items(id) -= 1
    if items(id) == 0 items - id
    itemCount -= 1
  }

  def emptyCart(): Unit = {
    items.empty()
    itemCount = 0
  }
}