package actors

import scala.concurrent.duration._
import akka.actor.{Actor, ActorRef, Props, Timers}
import actors.Checkout
import akka.event.LoggingReceive
import fsm.Cart.Event
import fsm.Checkout

case object Cart {
  sealed trait Command
  case class AddItem(id: String) extends Command
  case class RemoveItem(id: String) extends Command
  case object StartCheckout extends Command
  sealed trait Event
  case class ItemAdded(id: String) extends Event
  case class ItemRemoved(id: String) extends Event
  case class CheckoutStarted(actor: ActorRef) extends Event
  case object CheckoutCancelled extends Event
  case class CheckoutClosed(delivery: Checkout.Delivery.DeliveryType, payment: Checkout.PaymentMethod.PaymentType) extends Event

  private case object CartTimer
  private case object Timeout
}

class Cart extends Actor with Timers{

  val cartExpirationTime: FiniteDuration = 30.seconds
  var items = collection.mutable.Map[String, Integer]()
  var itemCount = 0
  def receive = LoggingReceive{empty}

  def empty: Receive = {
    case Cart.AddItem(id) => {
      addItem(id)
      context become LoggingReceive{nonEmpty}
      sender ! Cart.ItemAdded(id)
      onEntryNonEmpty()
    }
  }

  def nonEmpty(): Receive = {
    case Cart.AddItem(id) => {
      addItem(id)
      sender ! Cart.ItemAdded(id)
    }
    case Cart.RemoveItem(id) if itemCount == 1 => {
      removeItem(id)
      onExitNonEmpty()
      context become LoggingReceive{empty}
      onEntryEmpty()
      sender ! Cart.ItemRemoved(id)
    }
    case Cart.RemoveItem(id) if itemCount > 1 => {
      removeItem(id)
      sender ! Cart.ItemRemoved(id)
    }
    case Cart.StartCheckout => {
      onExitNonEmpty()
      context become LoggingReceive{inCheckout(sender)}
      onEntryInCheckout()
    }
    case Cart.Timeout => {
      onExitNonEmpty()
      context become LoggingReceive{empty}
      onEntryEmpty()
    }
  }

  def inCheckout(sender: ActorRef): Receive = {

    case Cart.CheckoutCancelled => {
      context become LoggingReceive{nonEmpty}
      onEntryNonEmpty()
    }

    case Cart.CheckoutClosed(delivery, method) => {
      onEntryEmpty()
      context become LoggingReceive{empty}

    }
  }

  def onEntryEmpty(): Unit = {
    emptyCart()
  }

  def onEntryNonEmpty(): Unit = {
    println("nonEmpty state!")
    timers.startSingleTimer(Cart.CartTimer, Cart.Timeout, cartExpirationTime)
  }

  def onExitNonEmpty(): Unit = {
    timers.cancel(Cart.CartTimer)
  }

  def onEntryInCheckout(): Unit = {
    val checkout = context.actorOf(Props[Checkout], "checkout")
    checkout ! Checkout.Start
    sender ! Cart.CheckoutStarted(checkout)
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
    if (items(id) == 0) items - id
    itemCount -= 1
  }

  def emptyCart(): Unit = {
    items.empty
    itemCount = 0
  }
}