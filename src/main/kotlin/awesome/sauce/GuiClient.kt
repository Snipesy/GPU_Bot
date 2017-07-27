/*
 * Copyright (c) 2017.
 *
 * This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package awesome.sauce


import com.aparapi.device.Device
import com.aparapi.device.OpenCLDevice
import java.awt.BorderLayout
import java.awt.event.ActionListener
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.jnativehook.keyboard.NativeKeyEvent
import org.jnativehook.keyboard.NativeKeyListener
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.text.StyleConstants
import javax.swing.text.SimpleAttributeSet

import javax.swing.SwingUtilities
import org.jnativehook.NativeHookException
import org.jnativehook.GlobalScreen
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent


/**
 * Very simple gui client.
 *
 * The gpuj depth options ,device selection, etc, don't actaully do anything right now.
 */
class GuiClient : ClientCallbackListener, NativeKeyListener {

    val mContext = this

    var gpu_depth_options = arrayOf("1", "2", "3", "4", "5")

    var gpu_abstraction_layers = arrayOf("1", "2")

    var botJob: Job? = null


    var statusTextArea: JTextPane

    val buttonStopMessage = "STOP (CTRL 2)"

    val buttonStartMessage = "START (CTRL 2)"

    val buttonErrorMessage = "Error"

    val abstractionLayers2 = JComboBox(gpu_depth_options)

    val abstractionLayers = JComboBox(gpu_abstraction_layers)

    val startStopButton = JButton(buttonStopMessage)

    var mDevices: List<OpenCLDevice>? = null



    val deviceSelector  = try {
        val devices = OpenCLDevice.listDevices(Device.TYPE.GPU)





        val cpus = OpenCLDevice.listDevices(Device.TYPE.CPU)




        devices.addAll(cpus)





        devices!!.forEach {
            System.out.println("Device: " + it.name)
        }

        if (devices.isEmpty())
            throw Exception("No devices")

        mDevices = devices

        val devMap = devices.map {it.name}.toMutableList()

        devMap.add("CPU (JTP)")

        abstractionLayers.selectedIndex = 1

        abstractionLayers2.selectedIndex = 2

        val initialDevice = 0



        botJob = launch(CommonPool)
        {
            val devs = mDevices
            if (devs == null)
            {
                // Do nothing
            }
            else
                MainClient.main(mContext, abstractionLayers.selectedIndex+1, abstractionLayers2.selectedIndex+1, devs[initialDevice])
        }


        JComboBox(devMap.toTypedArray())

    }
    catch (e: UnsatisfiedLinkError)
    {

        statusUpdate("Looks we don't have the proper tools. \nOpenCL or video drivers? 64 bit Java?")

        startStopButton.text = buttonErrorMessage

        JComboBox(arrayOf("None"))
    }


    init {
        val guiFrame = JFrame()


        guiFrame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        guiFrame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                val job = botJob
                if (job != null)
                {
                    job.cancel()
                    runBlocking {
                        job.join()
                    }

                }
            }
        })
        guiFrame.title = "Quantum Bilge"

        guiFrame.setSize(500, 135)
        guiFrame.minimumSize = Dimension(500,135)
        guiFrame.maximumSize = Dimension(500,135)




        startStopButton.addActionListener(object : ActionListener {
            override fun actionPerformed(event: ActionEvent) {

                startStopButtonPushed()

            }
        })

        statusTextArea = JTextPane()

        statusTextArea.isEditable = false

        statusTextArea.background = guiFrame.background

        guiFrame.setLocationRelativeTo(null)

        val comboPanel = JPanel()
        val comboLbl = JLabel("Abstraction")


        comboPanel.add(comboLbl)
        comboPanel.add(abstractionLayers)

        val comboLbl2 = JLabel("GPU Depth")



        val comboLbl3 = JLabel("Compute Device")


        comboPanel.add(comboLbl2)
        comboPanel.add(abstractionLayers2)






        comboPanel.add(comboLbl3)
        comboPanel.add(deviceSelector)





        guiFrame.add(startStopButton, BorderLayout.PAGE_END)
        guiFrame.add(comboPanel, BorderLayout.PAGE_START)
        guiFrame.add(statusTextArea, BorderLayout.CENTER)

        guiFrame.isVisible = true







    }

    val waitingMessage = "Waiting for closure (Click again to abort)"

    fun startStopButtonPushed()
    {
        if (startStopButton.text == buttonErrorMessage)
        {
            botJob?.cancel()
            runBlocking {
                botJob?.join()
            }
            return
        }
        else if (startStopButton.text == buttonStartMessage)
        {
            botJob?.cancel()
            runBlocking {
                val tmp = startStopButton.text
                startStopButton.text = "Please wait..."

                startStopButton.repaint()
                botJob?.join()
                startStopButton.text = tmp
            }

            if (startStopButton.text == buttonStartMessage)
            {
                botJob = launch(CommonPool)
                {
                    val devs = mDevices
                    if (devs == null)
                    {
                        // Do nothing
                    }
                    else if (deviceSelector.selectedIndex == devs.size)
                        MainClient.main(mContext, abstractionLayers.selectedIndex+1, abstractionLayers2.selectedIndex+1, null)
                    else
                        MainClient.main(mContext, abstractionLayers.selectedIndex+1, abstractionLayers2.selectedIndex+1, devs[deviceSelector.selectedIndex])
                }

                startStopButton.text = buttonStopMessage

            }
        }
        else if (startStopButton.text == buttonStopMessage)
        {
            botJob?.cancel()


        }
    }

    override fun onStop() {
        SwingUtilities.invokeLater {
            startStopButton.text = buttonStartMessage
        }
    }

    override fun statusUpdate(update: String)
    {
        SwingUtilities.invokeLater {

            statusTextArea.text = update

            val doc = statusTextArea.styledDocument
            val center = SimpleAttributeSet()
            StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER)
            doc.setParagraphAttributes(0, doc.length, center, false)
        }

    }

    var ctrlIsPressed = false

    var f2IsPressed = false

    override fun nativeKeyPressed(p0: NativeKeyEvent?) {
        if (p0 == null)
            return
        else if (p0.keyCode == NativeKeyEvent.VC_CONTROL_L || p0.keyCode == NativeKeyEvent.VC_CONTROL_R)
        {
            ctrlIsPressed = true
        }
        else if (p0.keyCode == NativeKeyEvent.VC_2)
        {
            f2IsPressed = true
        }

        if (ctrlIsPressed && f2IsPressed)
        {
            SwingUtilities.invokeLater {
                startStopButtonPushed()
            }
        }
    }

    override fun nativeKeyReleased(p0: NativeKeyEvent?) {
        if (p0 == null)
            return
        else if (p0.keyCode == NativeKeyEvent.VC_CONTROL_L || p0.keyCode == NativeKeyEvent.VC_CONTROL_R)
        {
            ctrlIsPressed = false
        }
        else if (p0.keyCode == NativeKeyEvent.VC_2)
        {
            f2IsPressed = false
        }
    }

    override fun nativeKeyTyped(p0: NativeKeyEvent?) {
        // DO nothing
    }




    companion object {

        @JvmStatic fun main(args: Array<String>) {

            val client = GuiClient()

            try {
                GlobalScreen.registerNativeHook()
            } catch (ex: NativeHookException) {
                System.err.println("There was a problem registering the native hook.")
                System.err.println(ex.message)
            }

            GlobalScreen.addNativeKeyListener(client)



        }
    }

}