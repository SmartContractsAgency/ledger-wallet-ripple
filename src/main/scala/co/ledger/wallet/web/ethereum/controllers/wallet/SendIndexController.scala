package co.ledger.wallet.web.ethereum.controllers.wallet

import biz.enef.angulate.Module.RichModule
import biz.enef.angulate.core.JQLite
import biz.enef.angulate.{Controller, Scope}
import co.ledger.wallet.core.wallet.ethereum.{Ether, EthereumAccount}
import co.ledger.wallet.web.ethereum.components.{QrCodeScanner, SnackBar}
import co.ledger.wallet.web.ethereum.core.utils.PermissionsHelper
import co.ledger.wallet.web.ethereum.services.{SessionService, WindowService}
import org.scalajs.dom

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.{Dynamic, timers}
import scala.util.{Failure, Success, Try}

/**
  *
  * SendIndexController
  * ledger-wallet-ethereum-chrome
  *
  * Created by Pierre Pollastri on 04/05/2016.
  *
  * The MIT License (MIT)
  *
  * Copyright (c) 2016 Ledger
  *
  * Permission is hereby granted, free of charge, to any person obtaining a copy
  * of this software and associated documentation files (the "Software"), to deal
  * in the Software without restriction, including without limitation the rights
  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  * copies of the Software, and to permit persons to whom the Software is
  * furnished to do so, subject to the following conditions:
  *
  * The above copyright notice and this permission notice shall be included in all
  * copies or substantial portions of the Software.
  *
  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  * SOFTWARE.
  *
  */
class SendIndexController(override val windowService: WindowService,
                          $location: js.Dynamic,
                          $route: js.Dynamic,
                          sessionService: SessionService,
                          $element: JQLite,
                          $scope: Scope) extends Controller with WalletController{

  var isScanning = false

  var address = ""
  var amount = ""
  var gasLimit = 9000
  private var _gasPrice = BigInt("21000000000")
  var gasPrice = new Ether(_gasPrice).toEther.toString()
  var total = Ether(0).toEther.toString()

  sessionService.currentSession.get.sessionPreferences.lift(SendIndexController.RestoreKey) foreach {(state) =>
    val restore = state.asInstanceOf[SendIndexController.RestoreState]
    address = restore.to
    if (restore.amount.isSuccess)
      amount = restore.amount.get.toEther.toString()
  }

  def scanQrCode() = {
    PermissionsHelper.requestIfNecessary("videoCapture") map {(hasPermission) =>
      if (!hasPermission)
        throw new Exception("No Video capture permission")
      ()
    } onComplete {
      case Success(_) =>
        isScanning = true
        scanner.start()
        $scope.$digest()
      case Failure(_) =>

    }
  }

  def computeTotal(): Ether = {
    val t = getAmountInput().map((amount) => amount + (_gasPrice * 2100)).map(new Ether(_)).getOrElse(Ether(0))
    total = t.toEther.toString()
    t
  }

  def cancelScanQrCode() = {
    isScanning = false
    scanner.stop()
  }

  def getAmountInput(): Try[BigInt] = {
    Try((BigDecimal(amount) * BigDecimal(10).pow(18)).toBigInt())
  }

  def getAddressInput(): Try[EthereumAccount] = {
    Try(EthereumAccount(address))
  }

  def updateGasPrice(): Unit = {
    import timers._
    sessionService.currentSession.get.wallet.estimatedGasPrice() foreach {(price) =>
      _gasPrice = price.toBigInt
      gasPrice = price.toEther.toString()
      computeTotal()
      setTimeout(0) {
        $scope.$digest()
      }
    }
  }
  updateGasPrice()

  def send() = {
    try {
      val value = getAmountInput()
      val recipient = getAddressInput()
      if (value.isFailure) {
        SnackBar.error("Error", "Bad amount").show()
      } else if (recipient.isFailure) {
        SnackBar.error("Error", "Bad address").show()
      } else {
        val isIban = true
        val fees = BigDecimal(900000)
        val gasPrice = _gasPrice
        println(s"Amount: $amount")
        println(s"Recipient: $address")
        println(s"Is IBAN: $isIban")
        println(s"Fees: $fees")
        sessionService.currentSession.get.wallet.balance() foreach {(balance) =>
          if (computeTotal() > balance) {
            SnackBar.error("Error", "Insufficient funds").show()
          } else {
            $location.path(s"/send/${value.get.toString()}/to/$address/from/0/with/$fees/price/$gasPrice")
            $scope.$apply()
          }
        }
      }
    } catch {
      case any: Throwable =>
        any.printStackTrace()
        // Display error message
    }
    //
  }

  private val scanner = $element.find("qrcodescanner").scope().asInstanceOf[QrCodeScanner.Controller]

  scanner.$on("qr-code", { (event: js.Any, value: String) =>
    cancelScanQrCode()
    address = value.replace("iban:", "")
    $scope.$apply()
  })

  $scope.$on("$destroy", {() =>
    val amount = Try((BigDecimal($element.find("#amount_input").asInstanceOf[JQLite].`val`().toString) * BigDecimal(10).pow(18)).toBigInt())
    val recipient = $element.find("#receiver_input").asInstanceOf[JQLite].`val`().toString
    sessionService.currentSession.get.sessionPreferences(SendIndexController.RestoreKey) =  SendIndexController.RestoreState(amount.map(new Ether(_)), recipient)
  })
}

object SendIndexController {
  def init(module: RichModule) = module.controllerOf[SendIndexController]("SendIndexController")

  val RestoreKey = "SendIndexController#Restore"
  case class RestoreState(amount: Try[Ether], to: String)
}