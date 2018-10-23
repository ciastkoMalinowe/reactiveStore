package fsm

import scala.concurrent.duration._
import akka.actor.{Actor, ActorRef, FSM, Props, Timers}
import fsm.Checkout
import fsm.Cart.AddItem

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

trait CartState
case object Empty extends CartState
case object NonEmpty extends CartState
case object InCheckout extends CartState

case class Data(itemCount: Int, items: Map[String, Int])

class Cart extends FSM[CartState, Data] with Timers{

  val cartExpirationTime: FiniteDuration = 30.seconds
  var checkout: ActorRef = null

  startWith(Empty, Data(0, Map[String, Int]()))

  when(Empty) {
    case Event(AddItem(id), stateData) => {
      val map: Map[String, Int] = stateData.items + (id -> 1)
      val data = Data(0, map)
      goto(NonEmpty) using(data) replying(Cart.ItemAdded(id))
    }
  }

  when(NonEmpty) {
    case Event(AddItem(id), stateData) if stateData.items.getOrElse(id, null) != null => {
      val num: Int = stateData.items.get(id).get + 1
      val map: Map[String, Int] = stateData.items + (id -> num)
      val data = Data(stateData.itemCount + 1, map)
      stay using data replying Cart.ItemAdded(id)
    }
    case Event(AddItem(id), stateData) if stateData.items.getOrElse(id, null) == null => {
      val map: Map[String, Int] = stateData.items + (id -> 1)
      val data = Data(stateData.itemCount + 1, map)
      stay using data replying Cart.ItemAdded(id)
    }
    case Event(Cart.RemoveItem(id), stateData) if stateData.items.get(id).get == 1 => {
      val map: Map[String, Int] = stateData.items - id
      val data = Data(stateData.itemCount - 1, map)
      data.itemCount match {
        case 0 => goto(Empty) using data replying Cart.ItemRemoved(id)
        case _ => stay using data replying Cart.ItemRemoved(id)
      }
    }
    case Event(Cart.RemoveItem(id), stateData) if stateData.items.get(id).get > 1 => {
      val num: Int = stateData.items.get(id).get - 1
      val map: Map[String, Int] = stateData.items - id + (id -> num)
      val data = Data(stateData.itemCount - 1, map)
      stay using data replying Cart.ItemRemoved(id)
    }

    case Event(Cart.StartCheckout, stateData) => {
      checkout = context.actorOf(Props[Checkout], "checkout")
      goto(InCheckout) using(stateData) replying Cart.CheckoutStarted(checkout)
    }
    case Event(Cart.Timeout, stateData) => {
      goto(Empty) using Data(0, Map[String, Int]())
    }
  }

  when(InCheckout) {

    case Event(Cart.CheckoutCancelled, data) => {
      goto(NonEmpty) using data
    }

    case Event(Cart.CheckoutClosed(deliver, payment), data) => {
      goto(Empty) using Data(0, Map[String, Int]())
    }
  }

  onTransition {
    case _ -> NonEmpty => timers.startSingleTimer(Cart.CartTimer, Cart.Timeout, cartExpirationTime)
    case NonEmpty -> Empty => timers.cancel()
    case NonEmpty -> InCheckout => {
      timers.cancel()
    }
  }
}
