import actors.{Cart, Checkout}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.LoggingReceive

import scala.concurrent.Await
import scala.concurrent.duration.Duration

case object SimulateAddingAndRemoving
case object SimulateCheckout
case object SimulateTimeout

class CartManager extends Actor {

  val cart = context.actorOf(Props[Cart], "cart")
  var chRef: ActorRef = null

  def receive = LoggingReceive{
    case SimulateAddingAndRemoving => sAddingAndRemoving()
    case SimulateCheckout => sCheckout()
    case SimulateTimeout => sTimeout()

    case Cart.ItemAdded(id) =>
      println("Added " + id)
    case Cart.ItemRemoved(id) =>
      println("Removed " + id)

    case Cart.CheckoutStarted(ref) => {
      println("Checkout started")
      chRef = ref
    }
  }

  def sAddingAndRemoving(): Unit ={

    cart ! Cart.RemoveItem("1")
    cart ! Cart.AddItem("1")
    cart ! Cart.RemoveItem("1")
    cart ! Cart.StartCheckout
    cart ! Cart.AddItem("2")
    cart ! Cart.AddItem("3")
    cart ! Cart.StartCheckout
  }

  def sCheckout(): Unit = {
    chRef ! Checkout.SelectDelivery(Checkout.Delivery.Post)
    chRef ! Checkout.SelectPayment(Checkout.PaymentMethod.CreditCard)
    chRef ! Checkout.ReceivePayment
  }

  def sTimeout(): Unit ={

    cart ! Cart.AddItem("10")
  }

}

object StoreApp extends App {
  val system = ActorSystem("ReactiveStore")
  val main = system.actorOf(Props[CartManager], "main")

  main ! SimulateAddingAndRemoving
  Thread.sleep(1000)
  main ! SimulateCheckout
  Thread.sleep(10000)
  main ! SimulateTimeout
  Thread.sleep(35000)
  Await.result(system.whenTerminated, Duration.Inf)
}