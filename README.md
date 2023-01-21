# ControlX2

Android app for phones and Wear OS watches which controls a Tandem t:slim X2 pump with [PumpX2](https://github.com/jwoglom/pumpx2). 

> **Warning**\
> **This application is for EXPERIMENTAL USE ONLY and can be used to MODIFY ACTIVE INSULIN DELIVERY ON YOUR INSULIN PUMP.**\
> There is NO WARRANTY IMPLIED OR EXPRESSED DUE TO USE OF THIS SOFTWARE.\
> YOU ASSUME ALL RISK FOR ANY MALFUNCTIONS, BUGS, OR INSULIN DELIVERY ACTIONS.\
> \
> It is NOT AFFILIATED WITH OR SUPPORTED by Tandem, Dexcom, or any other manufacturer.\
> **It has not been officially approved for use and is provided as a RESEARCH TOOL ONLY.**

<div>
<img src="https://user-images.githubusercontent.com/192620/213893870-9a3954db-7482-458f-955a-16e2f13a99d1.png" alt="Bolus from phone" title="Bolus from phone" width=300 />
<img src="https://user-images.githubusercontent.com/192620/213893894-a2e63958-3934-47cc-86f2-6eaa6d1212c9.png" alt="Bolus delivery from phone" title="Bolus delivery from phone" width=300 />
</div>

<div>
<img src="https://user-images.githubusercontent.com/192620/213893946-4aba8e56-cfa5-4166-9e7b-2cd570bd6e2a.png" alt="Bolus from watch" title="Bolus from watch" height=300 />
<img src="https://user-images.githubusercontent.com/192620/206879740-0f0b2a03-8b9d-4c63-b806-a19b23c44675.png" alt="Bolus delivery from watch" title="Bolus delivery from watch" height=300 />
</div>

**Supported features:**

* Viewing pump status information (IOB, cartridge amount, battery, basal rate) 
* Viewing connected CGM information (current reading, arrow trend, sensor and transmitter status)
* Delivering a bolus (with units, carbs, and BG amounts) from wearable
* Delivering a bolus (with units, carbs, and BG amounts) from phone
* Accepting/rejecting insulin corrections 
* Bolus calculator (to compute units based on entered carbs and BG)
* Bolus cancellation

**Work in progress features:**

* Watch face complications
* App background service stability
* Mobile UI

**Planned features:**

* Watch tile
* Show CGM history graph
* Send pump alerts/alarms as notifications
* Upload data to Nightscout


<table border=0><tr valign=top>
<td>
<a href="https://www.youtube.com/watch?v=FybrFaLCs9Y">
<b>Demo video from phone:</b>
<br />
<img src="https://img.youtube.com/vi/FybrFaLCs9Y/0.jpg" width=300 alt="PumpX2 Alpha for Android - December 2022" title="PumpX2 Alpha for Android - December 2022" />
</a>
</td>
<td>
<a href="https://www.youtube.com/watch?v=jUUlqxDBQdQ">
<b>Demo video from watch:</b>
<br />
<img src="https://img.youtube.com/vi/jUUlqxDBQdQ/0.jpg" width=300 alt="PumpX2 Alpha for Wear OS - December 2022" title="PumpX2 Alpha for Wear OS - December 2022" />
</a>
</td>
</tr></table>

### Phone Screenshots
<div>
<img src="https://user-images.githubusercontent.com/192620/213893772-d1fcd8e7-7e7f-41d3-ad47-3e856dfdeb93.png" alt="App home screen" title="App home screen" height=450 />
<img src="https://user-images.githubusercontent.com/192620/213893867-2e08401f-ca06-4cdc-8a32-9ac2a0cc1d3b.png" alt="Bolus window" title="Bolus window" height=450 />
<img src="https://user-images.githubusercontent.com/192620/213893870-9a3954db-7482-458f-955a-16e2f13a99d1.png" alt="Bolus correction" title="Bolus correction" height=450 />
<img src="https://user-images.githubusercontent.com/192620/213893894-a2e63958-3934-47cc-86f2-6eaa6d1212c9.png" alt="Bolus delivery" title="Bolus delivery" height=450 />
<img src="https://user-images.githubusercontent.com/192620/213893904-13b715cf-2bfa-46fb-b527-b641a4691ffa.png" alt="Bolus notification" title="Bolus notification" height=450 />
<img src="https://user-images.githubusercontent.com/192620/213893911-fac52b14-50f3-40ab-96ea-aacd9fedd63f.png" alt="Bolus cancellation" title="Bolus cancellation" height=450 />
<img src="https://user-images.githubusercontent.com/192620/213893923-152b07c7-3de3-45c0-b9d5-0c0d83a8af8b.png" alt="Bolus cancelled" title="Bolus cancelled" height=450 />
</div>

### Wear OS Screenshots

![App home screen](https://user-images.githubusercontent.com/192620/206879718-91b90287-dbad-4a9d-9905-a43144025a0c.png)
![Selecting bolus units](https://user-images.githubusercontent.com/192620/206879726-5c13adad-0c05-4786-8e1a-b4bd63faae7f.png)
![Selecting bolus carbs](https://user-images.githubusercontent.com/192620/206879731-cc83616b-d4f2-4f06-97ae-0577ebe30d94.png)
![Delivering a bolus](https://user-images.githubusercontent.com/192620/206879740-0f0b2a03-8b9d-4c63-b806-a19b23c44675.png)
![Delivering a bolus](https://user-images.githubusercontent.com/192620/206879749-5b6f6e32-2573-4f7d-acb4-b18448a5d880.png)
![Delivering a bolus](https://user-images.githubusercontent.com/192620/206879759-6ec60327-8d6a-45ae-9f94-4900dbbbc6cd.png)


## Setup

Assumes that you have PumpX2 built and published to the local Maven repository. You can do this with:

```bash
$ git clone https://github.com/jwoglom/pumpx2
$ cd pumpx2
$ ./gradlew build
$ ./gradlew publishToMavenLocal 
```

The PumpX2 library files will be published to `$HOME/.m2/repository/com/jwoglom/pumpx2/`.

If the PumpX2 libraries are rebuilt, either bump the version number in both PumpX2 and ControlX2's gradle
configurations or run `./gradlew build --refresh-dependencies`.
