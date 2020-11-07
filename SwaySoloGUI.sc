SwaySoloGUI : Singleton {
	//Carl Testa 2020
	//GUI for Solo Version of Sway

	var <>win, testwin, testview, testcolumn, testrow, color_scheme, main, view, text, amp_sliders, step_slider, grav_slider, refresh_slider, volume_slider, fade_slider, updater, formatted_x, formatted_y;

   init {
		win = Window("Sway Solo", Rect(25, 600, 900, 450));
		win.onClose_({this.clear});

		color_scheme = [Color.white];

		main = View.new(win,Rect(0,0,900,450))
		//.decorator_(FlowLayout(Rect(0,0,1200,600),10@10,5@5));
		.layout_(HLayout());

		//Format the x/y coordinates to be input into the GUI
		formatted_x = Array.newClear(Sway.all.size);
		formatted_y = Array.newClear(Sway.all.size);

		Sway.all.keysValuesDo({|name, instance, n|
			formatted_x.put(n,instance.xy[0])
		});
		Sway.all.keysValuesDo({|name, instance, n|
			formatted_y.put(n,instance.xy[1])
		});

		//Begin building the view, I'm using an EnvelopeView here as the grid
		view = EnvelopeView(main)
		.thumbWidth_(60.0)
		.thumbHeight_(15.0)
		.drawLines_(false)
		.drawRects_(true)
		.step_(0.01)
		.selectionColor_(Color.red)
		.grid_(Point(0.5, 0.5))
		.gridOn_(true)
		.mouseDownAction_({|view|
			updater.pause;})
			//Tdef(("analysisLoop_"++view.index).asSymbol).pause;})
		.mouseUpAction_({|view|
			updater.resume(AppClock,1);})
			//Tdef(("analysisLoop_"++view.index).asSymbol).resume(AppClock,1);})
		.action_({|view|
			//model[view.index] = [view.x, view.y];
			//[view.index,view.x,view.y].postln;
		})

		.value_([formatted_x, formatted_y]);

		//TO DO: Put some kind of label on each quadrant so that you can see how the system changes over time
		//text = StaticText(view, Rect(40,40,100,100))
		//.string = Sway(\1).quadrant_map[2].source.asString;

		Sway.all.keysValuesDo({|name, instance, i|
			view.setString(i, instance.name);
			view.setFillColor(i,color_scheme[i%6]);
		});

		//Amp sliders
		Sway.all.keysValuesDo({|name, instance, i|
			var comp = View.new(main, 10@10)
	        .layout_(VLayout());
			Slider(comp, 10@10)
			.step_(0.01)
			.value_(Archive.global.at(("sway"++name++"thresholds").asSymbol).at(\amp)/40)
			.background_(color_scheme[i%6])
			.knobColor_(color_scheme[i%6])
			.action_({|val|
				instance.thresholds.put(\amp, val.value*40);
				(name++": amp threshold: "++(val.value*40)).postln;
				Archive.global.put(("sway"++name++"thresholds").asSymbol, instance.thresholds);
			});
			StaticText(comp, 10@10)
			.string_(("amp thresh"));
		});

		//density
		Sway.all.keysValuesDo({|name, instance, i|
			var comp = CompositeView.new(main, Rect(0,0,10,130))
	        .layout_(VLayout());
			Slider(comp, Rect(0,0,10,100))
			.step_(0.01)
			.value_(Archive.global.at(("sway"++name++"thresholds").asSymbol).at(\density)/5)
			.background_(color_scheme[i%6])
			.knobColor_(color_scheme[i%6])
			.action_({|val|
				instance.thresholds.put(\density, val.value*5);
				(name++": density threshold: "++(val.value*5)).postln;
				Archive.global.put(("sway"++name++"thresholds").asSymbol, instance.thresholds);
			});
			StaticText(comp, Rect(0,0,20,20))
			.string_(("dens thresh"));
		});

		//clarity
		Sway.all.keysValuesDo({|name, instance, i|
			var comp = CompositeView.new(main, Rect(0,0,10,130))
	        .layout_(VLayout());
			Slider(comp, Rect(0,0,10,100))
			.step_(0.01)
			.value_(Archive.global.at(("sway"++name++"thresholds").asSymbol).at(\clarity))
			.background_(color_scheme[i%6])
			.knobColor_(color_scheme[i%6])
			.action_({|val|
				instance.thresholds.put(\clarity, val.value);
				(name++": clarity threshold: "++(val.value)).postln;
				Archive.global.put(("sway"++name++"thresholds").asSymbol, instance.thresholds);
			});
			StaticText(comp, Rect(0,0,20,20))
			.string_(("clar thresh"));
		});

		//fadetime
		Sway.all.keysValuesDo({|name, instance, i|
			var comp = CompositeView.new(main, Rect(0,0,10,130))
	        .layout_(VLayout());
			Slider(comp, Rect(0,0,10,100))
			.step_(0.01)
			.value_(Archive.global.at(("sway"++name++"thresholds").asSymbol).at(\fadetime)/90)
			.background_(color_scheme[i%6])
			.knobColor_(color_scheme[i%6])
			.action_({|val|
				instance.fade_time(val.value*90);
				//instance.thresholds.put(\fadetime, val.value);
				(name++": fadetime: "++(val.value*90)).postln;
				Archive.global.put(("sway"++name++"thresholds").asSymbol, instance.thresholds);
			});
			StaticText(comp, Rect(0,0,20,20))
			.string_(("fadetime"));
		});

		/*
		//step
		step_slider = Slider(main, Rect(0,0,25,100))//step
			.step_(0.001)
		    .value_(Sway.step)
			.action_({|val|
				Sway.step=val.value;
			("step: "++(val.value)).postln;
			});

		//gravity
		grav_slider = Slider(main, Rect(0,0,25,100))//gravity
			.step_(0.0001)
		    .value_(Sway.gravity)
			.action_({|val|
				Sway.gravity=val.value;
			("gravity: "++(val.value)).postln;
			});

		//refresh
		refresh_slider = Slider(main, Rect(0,0,25,100))//refreshRate
			.step_(0.001)
		    .value_(Sway.refresh_rate/2)
			.action_({|val|
				Sway.refresh_rate=val.value*2+0.01;
			("refresh rate: "++(val.value*2+0.01)).postln;
			});

		*/
		//volume level slider (MAIN VOLUME)
		volume_slider = Slider(main, Rect(0,0,25,100))//mixerLevel
			.step_(0.01)
		    .value_(1.0)
		    .background_(Color.gray)
			.action_({|val|
			Sway.all.keysValuesDo({|name,instance,i|
				instance.output.set(\volume, val.value);
			//SwayConstructor(\sway).monitor.set(\level, val.value);
			("level: "++(val.value)).postln;
			});
		});

		win.front;

		//testing GUI
		testwin = Window("Sway-Controls", Rect(25, 50, 1200, 200));
		testview = FlowView(testwin);
		//turn verbose on and off for each channel
		Sway.all.keysValuesDo({|name, instance, i|
			Button(testview, Rect(0,0,100,50))
			.states_([
				[(name++" verbose off"), Color.black, Color.gray],
				[(name++" verbose on"), Color.black, Color.red]
			])
			.action_({|but|
				if(but.value==1, {instance.verbose=true},{instance.verbose=false});
				but.postln;
			});
		});
		//empty space
		View(testview, Rect(0,0,100,50));

		testview.startRow;

		//turn analysis on and off for each channel
		Sway.all.keysValuesDo({|name, instance, i|
			Button(testview, Rect(0,0,100,50))
			.states_([
				[(name++" analysis on"), Color.black, Color.green],
				[(name++" analysis off"), Color.black, Color.gray]
			])
			.action_({|but|
				if(but.value==0, {instance.analysis_on=true},{instance.analysis_on=false});
				but.postln;
			});
		});

		testview.startRow;
			//change processing type

		Button(testview, Rect(0,0,100,50))
		.states_([
			["silence", Color.black, Color.white]
		])
		.action_({|but|
			Sway.all.keysValuesDo({|name, instance, i|
				instance.silence;
			});
		});

		Button(testview, Rect(0,0,100,50))
		.states_([
			["reverb", Color.black, Color.white]
		])
		.action_({|but|
			Sway.all.keysValuesDo({|name, instance, i|
				instance.reverb;
			});
		});

		Button(testview, Rect(0,0,100,50))
		.states_([
			["amp mod", Color.black, Color.white]
		])
		.action_({|but|
			Sway.all.keysValuesDo({|name, instance, i|
				instance.ampmod;
			});
		});

		Button(testview, Rect(0,0,100,50))
		.states_([
			["delay", Color.black, Color.white]
		])
		.action_({|but|
			Sway.all.keysValuesDo({|name, instance, i|
				instance.delay;
			});
		});

		Button(testview, Rect(0,0,100,50))
		.states_([
			["freeze", Color.black, Color.white]
		])
		.action_({|but|
			Sway.all.keysValuesDo({|name, instance, i|
				instance.freeze;
			});
		});

		Button(testview, Rect(0,0,100,50))
		.states_([
			["pitchbend", Color.black, Color.white]
		])
		.action_({|but|
			Sway.all.keysValuesDo({|name, instance, i|
				instance.pitchbend;
			});
		});

		Button(testview, Rect(0,0,100,50))
		.states_([
			["filter", Color.black, Color.white]
		])
		.action_({|but|
			Sway.all.keysValuesDo({|name, instance, i|
				instance.filter;
			});
		});

		Button(testview, Rect(0,0,100,50))
		.states_([
			["granular", Color.black, Color.white]
		])
		.action_({|but|
			Sway.all.keysValuesDo({|name, instance, i|
				instance.granular;
			});
		});

		Button(testview, Rect(0,0,100,50))
		.states_([
			["textural", Color.black, Color.white]
		])
		.action_({|but|
			Sway.all.keysValuesDo({|name, instance, i|
				instance.textural;
			});
		});

		Button(testview, Rect(0,0,100,50))
		.states_([
			["cascade", Color.black, Color.white]
		])
		.action_({|but|
			Sway.all.keysValuesDo({|name, instance, i|
				instance.cascade;
			});
		});

		Button(testview, Rect(0,0,100,50))
		.states_([
			["waveloss", Color.black, Color.white]
		])
		.action_({|but|
			Sway.all.keysValuesDo({|name, instance, i|
				instance.waveloss;
			});
		});

		testwin.front;

		//gui updater task
		updater = TaskProxy.new({ loop {
				Sway.all.keysValuesDo({|name, instance, i|
					view.selectIndex(i);
				    view.x_(instance.xy[0]);
					view.y_(instance.xy[1]);
				});
				Sway.refresh_rate.wait;
			}
		}).play(AppClock);

	}

}