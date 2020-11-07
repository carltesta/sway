Sway : Singleton {
	//Carl Testa 2018
	//Special Thanks to Brian Heim, Joshua Parmenter, Chris McDonald, Scott Carver
	//original defaults pre-video were <>refresh_rate=1.0, <>gravity=0.01, <>step=0.05, default rates
	classvar <>short_win=1, <>long_win=30, <>refresh_rate=0.0625, <>gravity=0.002, <>step=0.002;

	var <>xy, <>quadrant, <>quadrant_names, <>quadrant_map, <>input, <>output, <>analysis_input, <>buffer, <>fftbuffer, <>delaybuffer, <>recorder, <>processing, <>fade=45, <>onsets, <>amplitude, <>clarity, <>flatness, <>amfreq, <>rvmix, <>rvsize, <>rvdamp, <>delaytime, <>delayfeedback, <>delaysourcevol, <>delaylatch, <>pbtime, <>pbbend, <>graintrig, <>grainfreq, <>grainpos, <>grainsize, <>granpos, <>granenvspeed, <>granrate, <>filtfreq, <>filtrq, <>freezedurmin, <>freezedurmax, <>freezeleg, <>texturalmin, <>texturalmax, <>texturalsusmin, <>texturalsusmax, <>texturalposrate, <>texturalpostype, <>texturalrate,
<>wldrop, <>wloutof, <>wlmode, <>analysis_loop, <>above_amp_thresh=false, <>above_clarity_thresh=false, <>above_density_thresh=false, <>thresholds, <>tracker, <>count=0, <>analysis_on=true, <>tracker_on=true, <>audio_processing=true, <>verbose=false, <>polarity=false, <>quadrant_flag=false, <>timelimit=200, <>available_processing, <>all_processing, <>global_change=false, <>quadrant_change=true;

    init {
		//Setup initial parameters
		this.reset;

		Routine.new({
			SynthDef(\textureStretch, {|bufnum, rate=1, posrate=0.1, postype=0, gSize=0.1 amp=0.5, out, gate=1|
			var sound, lfo, env;
			env = EnvGen.kr(Env.adsr(0.1,0.5,0.9,1,0.9,-1), gate, doneAction: 2);
			lfo = Select.kr(postype, LFTri.kr(posrate).unipolar, LFNoise0.kr(posrate).unipolar);
			sound = Warp1.ar(1, bufnum, lfo, rate, gSize, -1, 8, 0.1, 2) * env;
			Out.ar(out, sound*amp);
			}).add;

			SynthDef(\freeze, {
			|in = 0, out=0, buf1, buf2, amp=1, ratio=1, pitchd=0, pitcht=0, sourceVol=1, gate=1|
			var input, freeze1, env, fade, fadeTime=0.1, chain1, trig, pitch;
			trig = Trig.kr(\trigger.tr(1)).linlin(0,1,1,0);
			input = In.ar(in)*sourceVol.lag(0.1);
			chain1 = FFT(buf1, input);
			freeze1 = PV_Freeze(chain1, trig);
			freeze1 = IFFT(freeze1)*amp;
			env = EnvGen.kr(Env.adsr(0.1,0.5,0.9,1,0.9,-1), gate, doneAction: 2);
			Out.ar(out, freeze1*env);
			}).add;

			Server.default.sync;
		}).play;

		//audio input with chan argument
		input = NodeProxy.audio(Server.default, 1).fadeTime_(fade)
		.source = { |chan=0| SoundIn.ar(chan,1.neg) };

		//fft
		fftbuffer = Buffer.alloc(Server.default, 1024);

		//delaybuffer
		delaybuffer = Buffer.allocConsecutive(2, Server.default, 12*44100, 1);

		//audio recorder
		buffer = Buffer.alloc(Server.default, long_win*44100, 1);
		recorder = NodeProxy.audio(Server.default, 1)
		.source = {
			var off = Lag2.kr(A2K.kr(DetectSilence.ar(input.ar(1), 0.1), 0.3));
            var on = 1-off;
			var fade = MulAdd.new(on, 2, 1.neg);
			var out = XFade2.ar(Silent.ar(), input.ar(1), fade);
			RecordBuf.ar(out, buffer, loop: 1, run: on);
		};

		//this is the placeholder for audio procesing
		processing = NodeProxy.audio(Server.default, 1).fadeTime_(fade)
		.source = { Silent.ar(1) };

		//audio output to listen and change channel
		output = NodeProxy.audio(Server.default, 2)
		.source = { |volume=1| Pan2.ar(processing.ar(1), 0, volume) };

		//analysis input so there is option to decouple processed audio from analysed audio
		analysis_input = NodeProxy.audio(Server.default, 1)
		.source = { |chan=0| SoundIn.ar(chan) };

		//Build the analysis modules
		this.build_analysis;
		//Begin with an initial mapping of parameters
		this.nonpolarity_map;
		//this.polarity_map;
		//TO DO: How does the system switch between the polarity and nonpolarity map?

		//Longer term analysis controlling placement on processing grid
		analysis_loop = TaskProxy.new({ loop {
			//if analysis on flag is set to true then do analysis
			if(analysis_on==true, {
				//(this.name++": analysis on").postln;
			//if verbose is on report values
			if(verbose==true,{
			flatness.bus.get({|val|
				//(this.name++" flatness: "++val[1]).postln;
					});
			onsets.bus.get({|val|
				(this.name++" onsets: "++val[1]).postln;
					});
			clarity.bus.get({|val|
				(this.name++" clarity: "++val[1]).postln;
					});
					"----------------------------------".postln;
				});

			//if signal is above amplitude threshold do analysis
			amplitude.bus.get({|val|
				if(verbose==true,{(this.name++" amp: "++val[1]).postln});
				if( val[0] > thresholds.at(\amp), {//change it so that the amplitude thresh boolean is more accurate
					above_amp_thresh=true;
				}, {above_amp_thresh=false;});

				if( val[1] > thresholds.at(\amp), { //conduct analysis if longer averaged amp is above threshold
					if(verbose==true,{(this.name++" amp threshold reached").postln});
					clarity.bus.get({|val|
						//if(verbose==true,{(this.name++" clarity: "++val[1]).postln});
						if( val[1] > thresholds.at(\clarity),
							{above_clarity_thresh=true;
							xy[0]=(xy[0]+step).clip(0,1)},
							{above_clarity_thresh=false;
							xy[0]=(xy[0]-step).clip(0,1)});
					});
					onsets.bus.get({|val|
						//if(verbose==true,{(this.name++" onsets: "++val[1]).postln});
						if( val[1] > thresholds.at(\density),
							{above_density_thresh=true;
							xy[1]=(xy[1]+step).clip(0,1)},
							{above_density_thresh=false;
							xy[1]=(xy[1]-step).clip(0,1)});
					});
					//("analysis movement: "++xy).postln;
				}, {
			//else if below threshold drift to center
					if(verbose==true,{(this.name++" drift to center").postln});
					if(xy[0] > 0.5, {
						(xy[0]=xy[0]-gravity).clip(0,0.5)},{
						(xy[0]=xy[0]+gravity).clip(0,0.5)});
					if(xy[1] > 0.5, {
						(xy[1]=xy[1]-gravity).clip(0,0.5)},{
						(xy[1]=xy[1]+gravity).clip(0,0.5)});
					//("drift to center: "++xy).postln;
				});
			});
		if (quadrant_change==true, {
		this.assign_quadrant(xy[0], xy[1]);
		//Checks to see if quadrant has changed, if so, it changes type of processing
		if (quadrant[0] == quadrant[1], {
					},{this.change_processing});
		if (quadrant_flag==true, {
				this.change_processing;
				quadrant_flag=false;
				},{});
		//Tracker processing grid changer is implemented here
			if (tracker_on==true, {
					if( tracker.any({|i,n|i>(timelimit*16)}), {//if any item in tracker is above timelimit
					//then choose new fadetime
					if(fade>30, {fade=2+(38.0.rand)},{fade=25+(35.0.rand)});
					this.fade_time(fade);
					(this.name++": fade time is now = "++fade).postln;
					//then choose new processing for that quadrant
					this.choose_new_processing(tracker.detectIndex({|i|i>timelimit}));
					(this.name++": processing grid changing").postln;
					if(verbose==false,{global_change=true;(this.name++": global change enabled").postln});
					quadrant_flag=true;
					tracker[tracker.detectIndex({|i|i>timelimit})]=0;
					//Change polarity for the hell of it
					if(polarity==false, {
						this.polarity_map;polarity=true;
						(this.name++": polarity mapping set").postln;
					},{
						this.nonpolarity_map;polarity=false;
						(this.name++": non-polarity mapping set").postln;

					});
				},{});
				});
			});
			});
		refresh_rate.wait;
		count=count+1;
		}}).play;
	}

	build_analysis {
		onsets = NodeProxy.control(Server.default, 2)
		.source = {
			//Density Tracker
			var buf = LocalBuf.new(512,1);
			var onsets = Onsets.kr(FFT(buf, analysis_input.ar(1)));
			var shortStats = OnsetStatistics.kr(onsets, short_win);
			var longStats = OnsetStatistics.kr(onsets, long_win);
			var shortValue = (shortStats[0]/short_win);
			var longValue = (longStats[0]/long_win);
			[shortValue, longValue];
		};

		amplitude = NodeProxy.control(Server.default, 2)
		.source = {
			//Amplitude Tracker
			var chain = FFT(LocalBuf(1024), analysis_input.ar(1));
            var loudness = Loudness.kr(chain);
			var shortAverage = AverageOutput.kr(loudness, Impulse.kr(short_win.reciprocal));
			var longAverage = AverageOutput.kr(loudness, Impulse.kr(long_win.reciprocal));
			[shortAverage, longAverage];
		};

		clarity = NodeProxy.control(Server.default, 2)
		.source = {
			var freq, hasFreq, shortAverage, longAverage;
			//Pitch hasfreq Tracker
			# freq, hasFreq = Pitch.kr(analysis_input.ar(1));
            shortAverage = AverageOutput.kr(hasFreq,Impulse.kr(short_win.reciprocal));
			longAverage = AverageOutput.kr(hasFreq,Impulse.kr(long_win.reciprocal));
			[shortAverage, longAverage];
		};

		flatness = NodeProxy.control(Server.default, 2)
		.source = {
			//Spectral Flatness Tracker
			var chain = FFT(LocalBuf(1024), analysis_input.ar(1));
            var flat = SpecFlatness.kr(chain);
			var shortAverage = AverageOutput.kr(flat, Impulse.kr(short_win.reciprocal));
			var longAverage = AverageOutput.kr(flat, Impulse.kr(long_win.reciprocal));
			[shortAverage, longAverage];
		};

		//Parameter Controls
		//amplitude modulation
		amfreq = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		//reverb
		rvmix = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		rvsize = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		rvdamp = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		//delay
		delaytime = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		delayfeedback = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		delaysourcevol = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		delaylatch = TaskProxy.new().play;
		//pitch bend
		pbbend = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		pbtime = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		//grains
		graintrig = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		grainfreq = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		grainpos = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		grainsize = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		//granular
		granrate = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		granpos = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		granenvspeed = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		//filter
		filtfreq = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		filtrq = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		//freeze
		freezedurmin = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		freezedurmax = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		freezeleg = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		//textural
		texturalmin = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		texturalmax = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		texturalsusmin = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		texturalsusmax = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		texturalposrate = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		texturalpostype = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		texturalrate = PatternProxy();
		//waveloss
		wldrop = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		wloutof = NodeProxy.control(Server.default, 1).fadeTime_(fade);
		wlmode = NodeProxy.control(Server.default, 1).fadeTime_(fade);

	}

	nonpolarity_map {
		//TO DO: Modulate the mapping somehow with additional analysis
		//Mapping analysis data to parameter controls
		//amplitude modulation
		amfreq.source = { amplitude.kr(1,0).linlin(0,30,1,14)};
		//reverb
		rvmix.source = { onsets.kr(1,0).linlin(0,6,0.5,1) };
		rvsize.source = { amplitude.kr(1,1).linlin(0,30,0.3,1) };
		rvdamp.source = { clarity.kr(1,0).linlin(0,1,1,0) };
		//delay
		delaytime.source = { onsets.kr(1,0).linexp(0,7,0.5,9) };
		delayfeedback.source = { onsets.kr(1,0).linlin(0,10,0.5,0.05) };
		delaysourcevol.source = { onsets.kr(1,0).linlin(0,10,0.7,1) };
		delaylatch.source = { loop {
			5.0.wait;
			if(0.5.coin, { processing.set(\trigger, 1, \toggle, 1) });
		} };
		//pitch bend
		pbtime.source = { onsets.kr(1,0).linlin(0,6,0.1,1) };
		pbbend.source = { amplitude.kr(1,0).linlin(0,10,0.75,1.5) };
		//grains
		graintrig.source = { clarity.kr(1,0).linlin(0,1,1,0) };
		grainfreq.source = { onsets.kr(1,0).linlin(0,6,20,4) };
		grainsize.source = { onsets.kr(1,0).linlin(0,6,0.01,2) };
		grainpos.source = { flatness.kr(1,0).linlin(0,1,1,0) };
		//granular
		granrate.source = { onsets.kr(1,0).linlin(0,6,0.7,1.3) };
		granpos.source = { clarity.kr(1,0).linlin(0,1,0.2,0.8) };
		granenvspeed.source = { onsets.kr(1,0).linlin(0,6,(1/6),4) };
		//filter
		filtfreq.source = { onsets.kr(1,0).linlin(0,6,500,5000) };
		filtrq.source = { amplitude.kr(1,0).linlin(0,10,0.3,0.8) };
		//freeze
		freezedurmin.source = { onsets.kr(1,0).linlin(0,6,2,0.25) };
		freezedurmax.source = { onsets.kr(1,0).linlin(0,6,4,0.5) };
		freezeleg.source = { onsets.kr(1,0).linlin(0,6,4,1.5) };
		//textural
		texturalmin.source = { onsets.kr(1,0).linlin(0,6,1.5,0.1) };
		texturalmax.source = { onsets.kr(1,0).linlin(0,6,3.0,2.0) };
		texturalsusmin.source = { onsets.kr(1,0).linlin(0,6,1.5,0.2) };
		texturalsusmax.source = { onsets.kr(1,0).linlin(0,6,3.0,2.0) };
		texturalposrate.source = { clarity.kr(1,0).linlin(0,1,0.5,0.01) };
		texturalpostype.source = { clarity.kr(1,0).linlin(0,1,1,0).round };
		texturalrate.source = Prand([1,0.5], inf);
		//waveloss
		wldrop.source = { onsets.kr(1,0).linlin(0,6,1,35) };
		wloutof.source = { onsets.kr(1,0).linlin(0,6,10,40) };
		wlmode.source = { clarity.kr(1,0).linlin(0,1,2,1).round };


	}

	polarity_map {
		//Mapping analysis data to parameter controls
		//amplitude modulation
		amfreq.source = { amplitude.kr(1,0).linlin(0,30,14,1)};
		//reverb

		rvmix.source = { onsets.kr(1,0).linlin(0,6,1.0,0.5) };
		rvsize.source = { amplitude.kr(1,1).linlin(0,30,0.9,0.7) };
		rvdamp.source = { clarity.kr(1,0).linlin(0,1,0.2,1) };

		//delay
		delaytime.source = { onsets.kr(1,0).linexp(0,7,9,0.5) };
		delayfeedback.source = { onsets.kr(1,0).linlin(0,10,0.05,0.5)};
		delaysourcevol.source = { onsets.kr(1,0).linlin(0,10,1,0.7) };
		delaylatch.source = { loop {
			0.5.wait;
			if(0.5.coin, { processing.set(\trigger, 1, \toggle, 1) });
		} };
		//pitch bend
		pbtime.source = { onsets.kr(1,0).linlin(0,6,1,1.1) };
		pbbend.source = { amplitude.kr(1,0).linlin(0,10,1.5,0.75) };
		//grains
		graintrig.source = { clarity.kr(1,0).linlin(0,1,0,1) };
		grainfreq.source = { onsets.kr(1,0).linlin(0,6,20,4) };
		grainsize.source = { onsets.kr(1,0).linlin(0,6,2,0.01) };
		grainpos.source = { flatness.kr(1,0).linlin(0,1,0,1) };
		//granular
		granrate.source = { onsets.kr(1,0).linlin(0,6,1.3,0.7) };
		granpos.source = { clarity.kr(1,0).linlin(0,1,0.8,0.2) };
		granenvspeed.source = { onsets.kr(1,0).linlin(0,6,4,(1/6)) };
		//filter
		filtfreq.source = { onsets.kr(1,0).linlin(0,6,5000,500) };
		filtrq.source = { amplitude.kr(1,0).linlin(0,10,0.9,0.3) };
		//freeze
		freezedurmin.source = { onsets.kr(1,0).linlin(0,6,0.25,2) };
		freezedurmax.source = { onsets.kr(1,0).linlin(0,6,0.5,4) };
		freezeleg.source = { onsets.kr(1,0).linlin(0,6,1.5,4) };
		//textural
		texturalmin.source = { onsets.kr(1,0).linlin(0,6,0.1,1.5) };
		texturalmax.source = { onsets.kr(1,0).linlin(0,6,2.0,3.0) };
		texturalsusmin.source = { onsets.kr(1,0).linlin(0,6,0.2,1.5) };
		texturalsusmax.source = { onsets.kr(1,0).linlin(0,6,2.0,3.0) };
		texturalposrate.source = { clarity.kr(1,0).linlin(0,1,0.01,0.5) };
		texturalpostype.source = { clarity.kr(1,0).linlin(0,1,0,1).round };
		texturalrate.source = Prand([4,2,0.5,0.25], inf);
		//waveloss
		wldrop.source = { onsets.kr(1,0).linlin(0,6,35,1) };
		wloutof.source = { onsets.kr(1,0).linlin(0,6,40,10) };
		wlmode.source = { clarity.kr(1,0).linlin(0,1,1,2).round };
	}

	//TO DO: I'm wondering if I should completely separate out the processing from the analysis into a separate class. I wonder if it might make it easier in the future to create different results from the Sway analysis. Like Sway analysis controls audio processing or lighting or both. But if I put in audio processing in the main class then I won't have a chance to easily just have lighting control. Or perhaps I'd want only lighting for the first half of the show and then start the audio processing mid-way.

	//Change processing to amplitude modulation
	ampmod {
		//control mapping:
		//amplitude -> freq
		processing.source = {
			var off = Lag2.kr(A2K.kr(DetectSilence.ar(input.ar(1),0.02),0.3));
            var on = 1-off;
			var fade = MulAdd.new(on, 2, 1.neg);
			var out = XFade2.ar(Silent.ar(), input.ar(1), fade);
			var sine = LFTri.ar(amfreq.kr(1), 0).unipolar;
		 	var am = out*sine;
			am;
		 };
		(this.name++": Amplitude Modulation").postln;
	}

	//Change processing to Pitch tracking Ring Modulator
	ringmod {
		//control mapping:
		//TBD
		processing.source = {
			var freq, hasFreq, setFreq, amp, mod, ring, verb;
			# freq, hasFreq = Pitch.kr(input.ar(1), ampThreshold: 0.02, median: 7);
			setFreq = Latch.kr(freq, hasFreq);
			//amp = Amplitude.kr(input.ar(1));
			mod = Saw.ar(setFreq);
			ring = input.ar(1)*mod;
			verb = FreeVerb.ar(in: ring, mix: rvmix.kr(1), room: rvsize.kr(1), damp: rvdamp.kr(1));
			verb;
		};
		(this.name++": Ring Modulation").postln;
	}

	//Change processing to reverb
	reverb {
		//control mapping:
		//onsets -> mix
		//amplitude -> roomsize
		processing.source = { FreeVerb.ar(in: input.ar(1), mix: rvmix.kr(1), room: rvsize.kr(1), damp: rvdamp.kr(1)) };
		(this.name++": Reverb").postln;
		}

	//Change processing to waveloss
	waveloss {
		//control mapping:
		//polarity -> deterministic loss/random loss
		processing.source = {
			var preverb, waveloss, reverb;
			preverb = FreeVerb.ar(in: input.ar(1), mix: rvmix.kr(1), room: rvsize.kr(1), damp: rvdamp.kr(1));
			waveloss = WaveLoss.ar(preverb, wldrop.kr(1), wloutof.kr(1), wlmode.kr(1));
			reverb = FreeVerb.ar(in: waveloss, mix: rvmix.kr(1), room: rvsize.kr(1), damp: rvdamp.kr(1));
			reverb*1.5;
		};
		(this.name++": WaveLoss").postln;
		}

	/*
	//Change processing to freeze
	freeze {
		//control mapping:
		//onsets -> freezefreq
		//amplitude ->
		processing.source = {
			//First use gate on input
			var off = Lag2.kr(A2K.kr(DetectSilence.ar(input.ar(1),0.05),0.3));
            var on = 1-off;
			var fade = MulAdd.new(on, 2, 1.neg);
			var out = XFade2.ar(Silent.ar(), input.ar(1), fade);
			//then begin freeze
			var freeze;
			var amplitude = Amplitude.kr(out);
			var trig = amplitude > 0.2;
			var gate = Gate.kr(LFClipNoise.kr(freezefreq.kr(1)),trig);
			var chain = FFT(fftbuffer, out);
			chain = PV_Freeze(chain, gate);
			freeze = XFade2.ar(Silent.ar(), IFFT(chain), Lag2.kr(gate,freezefade.kr(1)));
			freeze = FreeVerb.ar(freeze, rvmix.kr(1), rvsize.kr(1));
			freeze = HPF.ar(freeze, 20);
			freeze;
		};
		(this.name++": Freeze").postln;
	}
	*/

	//Alternate Freeze
	//Change processing to alternate freeze from norns sketch
	freeze {
		//control mapping
		//onsets -> duration
		//onsets -> legato
		//clarity ->
		//polarity ->
		processing.source = Pbind(
			\instrument, \freeze,
			\in, input.bus.index,
			\dur, Pwhite(Pfunc({freezedurmin.bus.getSynchronous}),Pfunc({freezedurmax.bus.getSynchronous}),inf),
			\trigger, 1,
			\buf1, fftbuffer.bufnum,
			\legato,Pkey(\dur)*Pfunc({freezeleg.bus.getSynchronous}),
			\amp, Pwhite(0.4,0.6),
			);
		(this.name++": Freeze Pattern").postln;
	}

	//Change processing to delay
	delay {
		//control mapping:
		//onsets -> delaytime
		//onsets -> feedback
		processing.source = {
			var time = Latch.kr(delaytime.kr(1), \trigger.tr);
			var feedback = delayfeedback.kr(1);
			var sourcevol = delaysourcevol.kr(1);
			var local = LocalIn.ar(1) + (input.ar(1)*sourcevol);
			var select = ToggleFF.kr(\toggle.tr(1.neg));
			var delay1 = BufDelayL.ar(delaybuffer[0], local, Latch.kr(time, 1- select));
			var delay2 = BufDelayL.ar(delaybuffer[1], local, Latch.kr(time, select));
			var fade = MulAdd.new(Lag2.kr(select, 4), 2, 1.neg);
			var delay = XFade2.ar(delay1, delay2, fade);
			LocalOut.ar(delay * feedback);
			delay;
		};
		(this.name++": Delay").postln;
	}
	//Change processing to pitch bend
	pitchbend {
		//control mapping:
		//onsets -> time
		//amplitude -> bend
		processing.source = {
			PitchShift.ar(input.ar(1), 1, pbbend.kr(1), 0.2, pbtime.kr(1))
		};
		(this.name++": Pitch Bend").postln;
	}

	filter {
		//control mapping:
		//onsets -> filtfreq
		//amplitude -> filtrq
		processing.source = {
			RLPF.ar(input.ar(1), filtfreq.kr(1), filtrq.kr(1)).tanh;
		};
		(this.name++": Filter").postln;
	}

	//Change processing to granular (from Sway 0.2)
	granular {
		processing.source = {
			//control mapping:
			//onsets -> granular rate
			//clarity -> granular position
			//onsets -> granular envelope speed
			var rate = granrate.kr(1);
			var envspeed = granenvspeed.kr(1);
			var pos = granpos.kr(1);
			var lfo = LFNoise1.kr({rate!6}).unipolar;
			var env = VarSaw.kr(envspeed, [0,1/6,2/6,3/6,4/6,5/6], 0.5, 0.2);
			var sound = Mix.new(Warp1.ar(1, buffer.bufnum, lfo, rate)*env);
			sound;
		};
		(this.name++": Granular").postln;
	}

	//Change processing to grains
	grains {
		//control mapping:
		//pitch -> trig
		//onsets -> size
		//onsets -> position
		processing.source = {
			var trigselect = graintrig.kr(1);
			var freq = grainfreq.kr(1);
			var posselect = grainpos.kr(1);
			var gSize = grainsize.kr(1);
		    var trig = SelectX.kr(trigselect, [Impulse.kr(freq), GaussTrig.kr(freq)]);
			//var pos = SelectX.kr(posselect, [LFSaw.kr(freq).unipolar, LFNoise2.kr(freq).unipolar]);
			var pos = SinOsc.ar(freq).unipolar*0.9;
		    var sound = GrainBuf.ar(1, trig, gSize, buffer.bufnum, 1, pos);
			sound = FreeVerb.ar(sound, rvmix.kr(1), rvsize.kr(1));
		    sound;
	    };
		(this.name++": Grains").postln;
	}

	//Change processing to textural synth from IRIS
	textural {
		//control mapping
		//onsets -> dur min/max
		//onsets -> sustain min/max
		//clarity -> position rate
		//polarity -> rate
		processing.source = Pbind(
			\instrument, \textureStretch,
			//\dur, Pwhite(0.1,3.0,inf),
			\dur, Pwhite(Pfunc({texturalmin.bus.getSynchronous}),Pfunc({texturalmax.bus.getSynchronous}),inf),
			//\sustain, Pwhite(0.05, 4.0, inf),
			\sustain, Pwhite(Pfunc({texturalsusmin.bus.getSynchronous}),Pfunc({texturalsusmax.bus.getSynchronous}),inf),
			\gSize, Pwhite(0.1,1.0,inf),
			\stretch, Pwhite(0.8,2.0,inf),
			\bufnum, buffer.bufnum,
			//\posrate, 0.1,
			\posrate, Pfunc({texturalposrate.bus.getSynchronous}),
			\rate, texturalrate,
			\amp, 0.35,
			);
		(this.name++": Textural").postln;
	}

	//TO DO: Work on this grain processing, perhaps change it to a PatternProxy running the texturestretch synthdef??

	//Change processing to grains
	grainer {
		//Silence NodeProxy
		processing.source = { Silent.ar(1) };
		PatternProxy.new()
		.source =
		//control mapping:
		//pitch -> trig
		//onsets -> size
		//onsets -> position
		processing.source = {
			var trigselect = graintrig.kr(1);
			var freq = grainfreq.kr(1);
			var posselect = grainpos.kr(1);
			var gSize = grainsize.kr(1);
		    var trig = SelectX.kr(trigselect, [Impulse.kr(freq), GaussTrig.kr(freq)]);
			var pos = SelectX.kr(posselect, [LFSaw.kr(freq).unipolar, LFNoise2.kr(freq).unipolar]);
		    var sound = GrainBuf.ar(1, trig, gSize, buffer.bufnum, 1, pos);
			sound = FreeVerb.ar(sound, rvmix.kr(1), rvsize.kr(1));
		    sound;
	    };
		(this.name++": Grainer").postln;
	}

	//change processing to cascade
	cascade {
		processing.source = {
			//TO DO: analysis control not implemented
			//pitch ->
			//amp -> number of layers??
			//onsets -> how far back in file does it read? Output current frame maybe?
			var sound = PlayBuf.ar(1, buffer.bufnum, 1, 0, {buffer.numFrames.rand}!16, 1);
			var env = SinOsc.kr(1/16, (0..15).linlin(0,15,8pi.neg,8pi), 0.2);
			var mix = Limiter.ar(Mix.new(sound*env), 1);
			mix;
		};
		(this.name++": Cascade").postln;
	}
	//Silence processing
	silence {
		processing.source = { Silent.ar(1) };
		(this.name++": Silence").postln;
	}

	//execute change in processing type
	change_processing {
		(quadrant_map[quadrant[0]]).value;
	}

	//assign which quadrant source is in based on x/y coordinates
	assign_quadrant { |x, y|
		quadrant = quadrant.shift(1);
		case
		    {(x<=0.55) && (x>=0.45) && (y<=0.55) && (y>=0.45)} {quadrant.put(0,0);tracker[0]=tracker[0]+1}//quadrant 0
		    {(x<=0.49) && (y<=0.49)} {quadrant.put(0,3);tracker[3]=tracker[3]+1}//quadrant 3
		    {(x>=0.51) && (y<=0.49)} {quadrant.put(0,4);tracker[4]=tracker[4]+1}//quadrant 4
		    {(x<=0.49) && (y>=0.51)} {quadrant.put(0,2);tracker[2]=tracker[2]+1}//quadrant 2
		    {(x>=0.55) && (y>=0.51)} {quadrant.put(0,1);tracker[1]=tracker[1]+1};//quadrant 1
	}

	//map different types of processing to the quadrants using the quadrant names
	map_quadrants {|names|
		names.do({|item,i|
			quadrant_map.put(i,all_processing.at(item));
			available_processing.removeAt(item);
			quadrant_names.put(i,item);
		});
	}

	//map single quadrant
	map_quadrant {|num, name|
		quadrant_map.put(num,all_processing.at(name));
		quadrant_names.put(num,name);
    }

	//change polarity
	change_polarity {
		if(polarity==false, {
			this.polarity_map;polarity=true;
			(this.name++": polarity mapping set").postln;
			},{
			this.nonpolarity_map;polarity=false;
			(this.name++": non-polarity mapping set").postln;
		});
	}

	//fade change
	fade_time { |time|
		//sound
		processing.fadeTime = time;
		//am
		amfreq.fadeTime = time;
		//reverb
		rvmix.fadeTime = time;
		rvsize.fadeTime = time;
		rvdamp.fadeTime = time;
		//delay
		delaytime.fadeTime = time;
		delayfeedback.fadeTime = time;
		//pitch bend
		pbbend.fadeTime = time;
		pbtime.fadeTime = time;
		//grains
		graintrig.fadeTime = time;
		grainfreq.fadeTime = time;
		grainpos.fadeTime = time;
		grainsize.fadeTime = time;
		//granular
		granrate.fadeTime = time;
		granpos.fadeTime = time;
		granenvspeed.fadeTime = time;
		//filter
		filtfreq.fadeTime = time;
		filtrq.fadeTime = time;
		//freeze
		freezedurmin.fadeTime = time;
		freezedurmax.fadeTime = time;
		freezeleg.fadeTime = time;
		//textural
		texturalmin.fadeTime = time;
		texturalmax.fadeTime = time;
		texturalsusmin.fadeTime = time;
		texturalsusmax.fadeTime = time;
		texturalposrate.fadeTime = time;
		texturalpostype.fadeTime = time;
		//texturalrate.fadeTime = time;
		//waveloss
		wldrop.fadeTime = time;
		wloutof.fadeTime = time;
		wlmode.fadeTime = time;
	}

	choose_new_processing {|qrant|
		//choose_new_processing function receives a quadrant as an argument and assigns an available processing to that quadrant
		var old, new;
		//don't remap the center, keep it silent
		if(qrant!=0, {
		//get current processing type which is now "old"
		old = quadrant_names[qrant];
		//change processing to one that is available and capture its symbol
		new = available_processing.keys.choose;
		quadrant_map.put(qrant, available_processing.at(new));
		//update quadrant_names
		quadrant_names.put(qrant, new);
		//remove new processing from available
		available_processing.removeAt(new);
		//place old processing in available
		available_processing.put(old, all_processing.at(old));
		},{});
	}

	reset {
		//reset to initial parameters
		//intial placement on processing grid
		xy = [0.5,0.5];//start in center
		tracker = [0,0,0,0,0];//number of times in each quadrant area
		//TO DO: the number of data structures I have to keep track of the quadrants and the names of the processing and all the available processing etc feels very clunky. There must be a better way to manage all this information.
		//Experimenting with Dictionary for Threshold Data structure
		thresholds = Dictionary.new;
		thresholds.putPairs([\amp, 4, \clarity, 0.6, \density, 1.5, \fadetime, 45]);
		//If an old archive of thresholds doesn't exist, create it with the default values
		if(Archive.global.at(("sway"++this.name++"thresholds").asSymbol).isNil, {
			Archive.global.put(("sway"++this.name++"thresholds").asSymbol, thresholds);},{});
		quadrant = Array.newClear(2);
		quadrant_map = Array.newClear(5);
		//change the initial mapping setup here:
		quadrant_names = Array.newClear(5);
		quadrant_names.put(0,\silence);
		quadrant_names.put(1,\delay);
		quadrant_names.put(2,\textural);
		quadrant_names.put(3,\waveloss);
		quadrant_names.put(4,\ampmod);
		all_processing = Dictionary.new;
		all_processing.put(\silence, {this.silence});
		all_processing.put(\delay, {this.delay});
		all_processing.put(\reverb, {this.reverb});
		all_processing.put(\ampmod, {this.ampmod});
		all_processing.put(\granular, {this.granular});
		all_processing.put(\textural, {this.textural});
		all_processing.put(\pitchbend, {this.pitchbend});
		all_processing.put(\cascade, {this.cascade});
		all_processing.put(\filter, {this.filter});
		all_processing.put(\freeze, {this.freeze});
		all_processing.put(\waveloss, {this.waveloss});
		//make all processing currently available
		available_processing = Dictionary.new;
		available_processing.put(\silence, {this.silence});
		available_processing.put(\delay, {this.delay});
		available_processing.put(\reverb, {this.reverb});
		available_processing.put(\ampmod, {this.ampmod});
		available_processing.put(\granular, {this.granular});
		available_processing.put(\textural, {this.textural});
		available_processing.put(\pitchbend, {this.pitchbend});
		available_processing.put(\cascade, {this.cascade});
		available_processing.put(\filter, {this.filter});
		available_processing.put(\freeze, {this.freeze});
		available_processing.put(\waveloss, {this.waveloss});
		this.assign_quadrant(xy[0], xy[1]);
		this.map_quadrants(quadrant_names);
		//this next line randomizies the quadrant space on startup
		5.do({|n|this.choose_new_processing(n)});
		polarity=false;
		global_change=false;
		//quadrant_flag=true;
	}

	end {
		output.free(1);
		input.free(1);
		analysis_input.free(1);
		buffer.free;
		fftbuffer.free;
		delaybuffer.do(_.free);
		recorder.free(1);
		processing.free(1);
		onsets.free(1);
		amplitude.free(1);
		clarity.free(1);
		flatness.free(1);
		amfreq.free(1);
		rvmix.free(1);
		rvsize.free(1);
		rvdamp.free(1);
		delaytime.free(1);
		delayfeedback.free(1);
		delaylatch.stop;
		pbtime.free(1);
		pbbend.free(1);
		graintrig.free(1);
		grainfreq.free(1);
		grainpos.free(1);
		grainsize.free(1);
		granpos.free(1);
		granenvspeed.free(1);
		granrate.free(1);
		filtfreq.free(1);
		filtrq.free(1);
		freezedurmin.free(1);
		freezedurmax.free(1);
		freezeleg.free(1);
		texturalmin.free(1);
		texturalmax.free(1);
		texturalsusmin.free(1);
		texturalsusmax.free(1);
		texturalposrate.free(1);
		texturalpostype.free(1);
		texturalrate.free(1);
		wldrop.free(1);
		wloutof.free(1);
		wlmode.free(1);
		analysis_loop.stop;
		this.clear;
		//Server.freeAll;
	}

}