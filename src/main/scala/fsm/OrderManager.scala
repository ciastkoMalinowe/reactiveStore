package fsm

import akka.actor.{ActorRef, FSM, Props, Timers}

import scala.concurrent.duration.FiniteDuration

object OrderManager {
  sealed trait State
  case object Uninitialized extends State
  case object Open          extends State
  case object InCheckout    extends State
  case object InPayment     extends State
  case object Finished      extends State

  sealed trait Command
  case class AddItem(id: String)                                               extends Command
  case class RemoveItem(id: String)                                            extends Command
  case class SelectDeliveryMethod(delivery: String)                            extends Command
  case class SelectPaymentMethod(payment: String)                              extends Command
  case object Buy                                                              extends Command
  case object Pay                                                              extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK


  sealed trait Data
  case class Empty()                                                           extends Data
  case class CartData(cartRef: ActorRef)                                       extends Data
  case class CartDataWithSender(cartRef: ActorRef, sender: ActorRef)           extends Data
  case class InCheckoutData(checkoutRef: ActorRef)                             extends Data
  case class InCheckoutDataWithSender(checkoutRef: ActorRef, sender: ActorRef) extends Data
  case class InPaymentData(paymentRef: ActorRef)                               extends Data
  case class InPaymentDataWithSender(paymentRef: ActorRef, sender: ActorRef)   extends Data
}

case class Actors(cart: Option[ActorRef], checkout: Option[ActorRef], payment: Option[ActorRef])

class OrderManager(expirationTime: FiniteDuration) extends FSM[OrderManager.State, Actors] with Timers {

  import fsm.OrderManager._

  startWith(Uninitialized, null)

  when(Uninitialized){
    case Event(AddItem(item: String), actors) => {
      val cart = context.actorOf(Props[Cart], "cart")
      cart ! Cart.AddItem(item)
      goto(Open) using Actors(Some(cart), None, None)
    }
  }

  when(Open){
    case Event(AddItem(item: String), actors) if actors.cart.isDefined => {
      actors.cart.get ! Cart.AddItem(item)
      stay
    }
    case Event(RemoveItem(item:String), actors) if actors.cart.isDefined => {
      actors.cart.get ! Cart.RemoveItem(item)
      stay
    }
    case Event(Buy, cart) => {
      goto(OrderManager.InCheckout) using cart
    }

  }

  when(OrderManager.InCheckout){
    case Event(SelectDeliveryMethod(delivery: String), cart) => {

    }
  }

  when(InPayment){

  }

  when(Finished){

  }



}
