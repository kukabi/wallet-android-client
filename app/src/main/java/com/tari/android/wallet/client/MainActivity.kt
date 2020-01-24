/**
 * Copyright 2019 The Tari Project
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the
 * following conditions are met:

 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.

 * 2. Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.

 * 3. Neither the name of the copyright holder nor the names of
 * its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.tari.android.wallet.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.graphics.PorterDuff
import android.os.AsyncTask
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import butterknife.*
import com.orhanobut.logger.Logger
import com.tari.android.wallet.model.MicroTari
import com.tari.android.wallet.model.Contact
import com.tari.android.wallet.model.Tx
import com.tari.android.wallet.model.TxId
import com.tari.android.wallet.service.TariWalletService
import com.tari.android.wallet.service.TariWalletServiceListener
import org.joda.time.format.DateTimeFormat
import java.lang.RuntimeException
import java.math.BigInteger
import kotlin.random.Random

/**
 * Main activity.
 */
class MainActivity : AppCompatActivity(), ServiceConnection {

    @BindView(R.id.main_text_view)
    lateinit var textView: TextView
    @BindView(R.id.main_btn_send_tari)
    lateinit var sendTariButton: Button
    @BindView(R.id.main_prog_bar)
    lateinit var progressBar: ProgressBar

    @BindString(R.string.send_taris_to)
    lateinit var sendTariString: String

    @BindColor(R.color.gray)
    @JvmField
    var grayColor = 0

    // wallet
    private var walletService: TariWalletService? = null
    // listener
    private val serviceListener = ServiceListener()

    private lateinit var sendMicroTariAmount: MicroTari
    private val sendMicroTariFee = MicroTari(BigInteger.valueOf(100L))
    private val minSendMicroTari = 500
    private val maxSendMicroTari = 1000000

    private val contacts = mutableListOf<Contact>()
    private lateinit var contactToSendTariTo: Contact
    private lateinit var walletPublicKeyHexString: String
    private val dateTimeFormat = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm")

    private var toast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ButterKnife.bind(this)

        sendTariButton.visibility = View.INVISIBLE
        progressBar.visibility = View.GONE
        progressBar.indeterminateDrawable
            .mutate()
            .setColorFilter(grayColor, PorterDuff.Mode.SRC_IN)

        // bind to service
        val implicit = Intent(TariWalletService::class.java.name)
        val matches: List<ResolveInfo> = packageManager.queryIntentServices(implicit, 0)
        when {
            matches.isEmpty() -> {
                toast = Toast.makeText(
                    this,
                    "Service not found.",
                    Toast.LENGTH_SHORT
                )
                toast?.show()
            }
            matches.size > 1 -> {
                toast = Toast.makeText(
                    this,
                    "Found multiple services.",
                    Toast.LENGTH_SHORT
                )
                toast?.show()
            }
            else -> {
                toast = Toast.makeText(
                    this,
                    "Located the wallet service. Connecting.",
                    Toast.LENGTH_SHORT
                )
                toast?.show()
                val explicit = Intent(implicit)
                val svcInfo: ServiceInfo = matches[0].serviceInfo
                val cn = ComponentName(
                    svcInfo.applicationInfo.packageName,
                    svcInfo.name
                )
                explicit.component = cn
                bindService(explicit, this, Context.BIND_AUTO_CREATE)
            }
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        Logger.e("Connected to the wallet service.")
        walletService = TariWalletService.Stub.asInterface(service)
        walletService!!.registerListener(serviceListener)
        // public key
        walletPublicKeyHexString = walletService!!.publicKeyHexString
        textView.post {
            displayBalanceAndContacts()
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Logger.e("Disconnected from the wallet service.")
        walletService = null
    }

    @OnClick(R.id.main_btn_send_tari)
    fun sendTariButtonClicked(view: View) {
        sendTariButton.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        textView.text = ""

        AsyncTask.execute {
            val sendSuccessful = walletService!!.send(
                contactToSendTariTo,
                sendMicroTariAmount,
                sendMicroTariFee,
                "Send ${sendMicroTariAmount.value} µTaris to ${contacts[0].alias}."
            )
            if (sendSuccessful) {
                textView.post {
                    toast?.cancel()
                    toast = Toast.makeText(
                        this,
                        "Tari sent successfully.",
                        Toast.LENGTH_SHORT
                    )
                    toast?.show()
                    displayBalanceAndContacts()
                }
            } else {
                toast?.cancel()
                textView.post {
                    toast = Toast.makeText(
                        this,
                        "Error: could not send Tari.",
                        Toast.LENGTH_SHORT
                    )
                    toast?.show()
                    displayBalanceAndContacts()
                }
            }
        }

    }

    /**
     * Displays wallet contacts in the text view.
     */
    private fun displayBalanceAndContacts() {
        if (walletService == null) {
            return
        }

        // balance
        val balanceInfo = walletService!!.balanceInfo
        var text = "BALANCE\n"
        text += "Available: ${balanceInfo.availableBalance.value} µTaris\n"
        text += "Pending Incoming: ${balanceInfo.pendingIncomingBalance.value} µTaris\n"
        text += "Pending Outgoing: ${balanceInfo.pendingOutgoingBalance.value} µTaris\n\n"

        sendTariButton.visibility = View.VISIBLE
        progressBar.visibility = View.GONE

        // contacts
        contacts.clear()
        contacts.addAll(walletService!!.contacts)
        text += "CONTACTS\n"
        for (contact in contacts) {
            text += contact.alias +
                    " [${contact.publicKeyHexString.take(7)}...${contact.publicKeyHexString.takeLast(
                        7
                    )}]\n"
        }

        contactToSendTariTo = contacts[Random.nextInt(contacts.size)]
        sendMicroTariAmount =
            MicroTari(BigInteger.valueOf(Random.nextInt(minSendMicroTari, maxSendMicroTari).toLong()))
        sendTariButton.text = String.format(
            sendTariString,
            sendMicroTariAmount.value.toLong(),
            contactToSendTariTo.alias
        )

        // txs
        text += "\nCOMPLETED TXS\n"
        val completedTxs = walletService!!.completedTxs.sortedWith(compareBy { it.timestamp })
        completedTxs.iterator().forEach {
            val from: String
            val to: String
            if (it.direction == Tx.Direction.INBOUND) {
                from = it.contact.alias
                to = "Me"
            } else {
                from = "Me"
                to = it.contact.alias
            }
            text += dateTimeFormat.print(it.timestamp.toLong() * 1000L) + " UTC : " +
                    from +
                    " -> $to " +
                    " : ${it.amount.value} µTaris\n"
        }

        text += "\nPENDING INBOUND TXS\n"
        val pendingInboundTxs =
            walletService!!.pendingInboundTxs.sortedWith(compareBy { it.timestamp })
        if (pendingInboundTxs.isEmpty()) {
            text += "-\n"
        } else {
            pendingInboundTxs.iterator().forEach {
                text += dateTimeFormat.print(it.timestamp.toLong() * 1000L) + " UTC : " +
                        " From ${it.contact.alias} " +
                        " : ${it.amount.value} µTaris\n"
            }
        }

        text += "\nPENDING OUTBOUND TXS\n"
        val pendingOutboundTxs =
            walletService!!.pendingOutboundTxs.sortedWith(compareBy { it.timestamp })
        pendingOutboundTxs.iterator().forEach {
            text += dateTimeFormat.print(it.timestamp.toLong() * 1000L) + " UTC : " +
                    " To ${it.contact.alias} " +
                    " : ${it.amount.value} µTaris\n"
        }

        textView.text = text

    }

    private fun getContactAliasFromPublicKeyHexString(
        contacts: List<Contact>,
        hexString: String
    ): String {
        if (hexString == walletPublicKeyHexString) {
            return "Me"
        } else {
            contacts.iterator().forEach {
                if (it.publicKeyHexString == hexString) {
                    return it.alias
                }
            }
        }
        throw RuntimeException("Contact not found.")
    }

    inner class ServiceListener : TariWalletServiceListener.Stub() {

        override fun onTxBroadcast(completedTxId: TxId) {
            Logger.e("Tx ${completedTxId.value} broadcast.")
            textView.post {
                Toast.makeText(
                    this@MainActivity,
                    "Tx ${completedTxId.value} broadcast.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        override fun onTxReceived(pendingInboundTxId: TxId) {
            Logger.e("Tx ${pendingInboundTxId.value} received.")
            textView.post {
                Toast.makeText(
                    this@MainActivity,
                    "Tx ${pendingInboundTxId.value} received.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        override fun onTxReplyReceived(pendingInboundTxId: TxId) {
            Logger.e("Tx $pendingInboundTxId reply received.")
            textView.post {
                Toast.makeText(
                    this@MainActivity,
                    "Tx $pendingInboundTxId reply received.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        override fun onTxFinalized(completedTxId: TxId) {
            Logger.e("Tx ${completedTxId.value} finalized.")
            textView.post {
                Toast.makeText(
                    this@MainActivity,
                    "Tx ${completedTxId.value} finalized.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        override fun onTxMined(completedTxId: TxId) {
            Logger.e("Tx ${completedTxId.value} mined.")
            textView.post {
                Toast.makeText(
                    this@MainActivity,
                    "Tx ${completedTxId.value} mined.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        override fun onDiscoveryComplete(txId: TxId, success: Boolean) {
            Logger.e("Discovery complete. Tx id: ${txId.value}. Success: $success.")
            textView.post {
                Toast.makeText(
                    this@MainActivity,
                    "Discovery complete. Tx id: ${txId.value}. Success: $success.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    }

}
