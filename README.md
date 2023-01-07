# WearX2

Android Wear OS and Phone companion app for controlling a Tandem pump with [PumpX2](https://github.com/jwoglom/pumpx2)
from a wearable.

<table border=0><tr valign=top><td>
<a href="https://www.youtube.com/watch?v=jUUlqxDBQdQ">
<b>Demo video from watch:</b>
<br />
<img src="https://img.youtube.com/vi/jUUlqxDBQdQ/0.jpg" width=300 alt="PumpX2 Alpha for Wear OS - December 2022" title="PumpX2 Alpha for Wear OS - December 2022" />
</a>

</td><td>
<a href="https://www.youtube.com/watch?v=FybrFaLCs9Y">
<b>Demo video from phone:</b>
<br />
<img src="https://img.youtube.com/vi/FybrFaLCs9Y/0.jpg" width=300 alt="PumpX2 Alpha for Android - December 2022" title="PumpX2 Alpha for Android - December 2022" />
</a>
</td></tr></table>

> **Warning**\
> **This application is for EXPERIMENTAL USE ONLY and can be used to MODIFY ACTIVE INSULIN DELIVERY ON YOUR INSULIN PUMP.**\
> There is NO WARRANTY IMPLIED OR EXPRESSED DUE TO USE OF THIS SOFTWARE. YOU ASSUME ALL RISK FOR ANY MALFUNCTIONS, BUGS, OR INSULIN DELIVERY ACTIONS.

**Supported features:**

* Viewing pump status information (IOB, cartridge amount, battery, basal rate) 
* Viewing connected CGM information (current reading, arrow trend, sensor and transmitter status)
* Delivering a bolus (with units, carbs, and BG amounts) from wearable
* Bolus calculator (to compute units based on entered carbs and BG)
* Bolus cancellation

**Work in progress features:**

* Watch face complications
* Delivering a bolus (with units, carbs, and BG amounts) from phone

**Planned features:**

* Watch tile
* Show CGM history graph
* Send pump alerts/alarms as notifications
* Upload data to Nightscout

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

If the PumpX2 libraries are rebuilt, either bump the version number in both PumpX2 and WearX2's gradle
configurations or run `./gradlew build --refresh-dependencies`.
