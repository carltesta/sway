# Sway
Sway for Desktop SuperCollider (live processing environment for one channel)

http://sway.carltesta.net

Demo/walkthrough video here: https://youtu.be/w9RZHmo4JAs

# Install

To install Sway in SuperCollider, first install the Singleton Quark
`Quarks.install("Singleton");`

You'll also need the SC3-Plugins. Download and instructions for install are here: http://supercollider.github.io/sc3-plugins/

In SuperCollider, goto `File -> Open user support directory`

Download the files in this git repo and move them into the `Extensions` directory

Recompile the class library by going to `Language -> Recompile Class Library`

# Usage

Create a new file and use the following code to start and configure the solo version of Sway

```
//Sway Solo

//create the instance of Sway
Sway(\solo);

(
//set configuration settings
Sway.short_win=1; //short analysis window (controls effect parameters)
Sway.long_win=30; //long analysis window (controls grid placement)
Sway.refresh_rate=1; //how often analysis runs
Sway.gravity=0.01; //how strong the pull to the center is
Sway.step=0.05; //how far grid placement can move within one step
Sway(\solo).input.set(\chan, 0); //what channel your audio input is in (default is 0)
Sway(\solo).analysis_input.set(\chan, 0); //what channel analysis should be derived from (default is 0)
)
(
//Listen to Processed Output
Sway(\solo).output.play;
//Start the GUI to adjust the settings and configuration
SwaySoloGUI.new;
)
```


