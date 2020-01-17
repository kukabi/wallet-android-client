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
import android.os.Bundle
import android.os.IBinder
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import butterknife.BindView
import butterknife.ButterKnife
import com.orhanobut.logger.Logger
import com.tari.android.wallet.service.TariWalletService

/**
 * Main activity.
 */
class MainActivity : AppCompatActivity(), ServiceConnection {

    @BindView(R.id.main_text_view)
    lateinit var textView: TextView

    private lateinit var walletService: TariWalletService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ButterKnife.bind(this)

        // bind to service
        val implicit = Intent(TariWalletService::class.java.name)
        val matches: List<ResolveInfo> = packageManager.queryIntentServices(implicit, 0)
        when {
            matches.isEmpty() -> {
                Toast.makeText(
                    this,
                    "Service not found.",
                    Toast.LENGTH_LONG
                ).show()
            }
            matches.size > 1 -> {
                Toast.makeText(
                    this,
                    "Found multiple services.",
                    Toast.LENGTH_LONG
                ).show()
                Logger.e("")
            }
            else -> {
                Toast.makeText(
                    this,
                    "Located the wallet service. Connecting.",
                    Toast.LENGTH_LONG
                ).show()
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
        textView.post {
            displayBalanceAndContacts()
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Logger.e("Disconnected from the wallet service.")
        // walletService = null
    }

    /**
     * Displays wallet contacts in the text view.
     */
    private fun displayBalanceAndContacts() {
        // get balance info
        val balanceInfo = walletService.balanceInfo
        // get contacts
        val contacts = walletService.contacts

        // display balance
        var text = "BALANCE\n\n"
        text += "Available Balance: ${balanceInfo.availableBalance} µTaris\n"
        text += "Pending Incoming Balance: ${balanceInfo.pendingIncomingBalance} µTaris\n"
        text += "Pending Outgoming Balance: ${balanceInfo.pendingOutgoingBalance} µTaris\n\n"

        // display contacts
        text += "CONTACTS\n\n"
        for (contact in contacts) {
            text += "${contact.alias}\n" +
                    "${contact.publicKeyHexString.take(15)}...${contact.publicKeyHexString.takeLast(15)}" +
                    "\n\n"
        }
        textView.text = text
    }

}
