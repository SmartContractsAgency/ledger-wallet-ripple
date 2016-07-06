package co.ledger.wallet.web.ethereum.controllers.wallet

import biz.enef.angulate.Module.RichModule
import biz.enef.angulate.core.Location
import biz.enef.angulate.{Controller, Scope}
import co.ledger.wallet.core.device.ethereum.LedgerApi
import co.ledger.wallet.core.device.ethereum.LedgerCommonApiInterface.LedgerApiException
import co.ledger.wallet.core.wallet.ethereum.EthereumAccount
import co.ledger.wallet.web.ethereum.components.SnackBar
import co.ledger.wallet.web.ethereum.services.{DeviceService, SessionService, WindowService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.util.{Failure, Success}

/**
  *
  * SendPerformViewController
  * ledger-wallet-ethereum-chrome
  *
  * Created by Pierre Pollastri on 10/05/2016.
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
class SendPerformController(windowService: WindowService,
                            $scope: Scope,
                            sessionService: SessionService,
                            deviceService: DeviceService,
                            $location: Location,
                            $route: js.Dynamic,
                            $routeParams: js.Dictionary[String]) extends Controller {

  implicit val ws: WindowService = windowService
  private val startGas = BigInt($routeParams("fees"))
  private val gasPrice = BigInt($routeParams("price"))
  private val accountId = $routeParams("account_id").toInt
  private val amount = BigInt($routeParams("amount"))
  private val to = EthereumAccount($routeParams("recipient").trim)
  sessionService.currentSession.get.wallet.account(accountId) flatMap {(account) =>
    account.ethereumAccountDerivationPath() flatMap {(from) =>
      account.transactionNonce() flatMap {(nonce) =>
        println(s"Transaction nonce: $nonce")
        deviceService.lastConnectedDevice() flatMap {(device) =>
          LedgerApi(device).signTransaction(
            nonce,
            gasPrice,
            startGas,
            from,
            to,
            amount,
            Array.empty[Byte]
          )
        }
      }
    }
  } flatMap {(signature) =>
    sessionService.currentSession.get.wallet.pushTransaction(signature.signedTx)
  } onComplete {
    case Success(_) =>
      sessionService.currentSession.get.sessionPreferences.remove(SendIndexController.RestoreKey)
      SnackBar.success("Transaction completed", "Successfully broadcasted to network").show()
      $location.url("/send")
      $route.reload()
    case Failure(ex: LedgerApiException) =>
      SnackBar.error("Transaction failed", "Transaction cancelled").show()
      sessionService.currentSession.get.sessionPreferences.remove(SendIndexController.RestoreKey)
      $location.url("/send")
      $route.reload()
    case Failure(ex) =>
      SnackBar.error("Transaction failed", "Transaction rejected by the server").show()
      $location.url("/send")
      $route.reload()
  }

}

object SendPerformController {
  def init(module: RichModule) = module.controllerOf[SendPerformController]("SendPerformController")
}