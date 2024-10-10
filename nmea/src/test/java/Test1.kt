package  moe.microbios.nmea

import java.math.BigDecimal
import kotlin.math.absoluteValue

val data = setOf(
    "\$GPGSA,A,1,,,,,,,,,,,,,,,,*32",
    "\$GPVTG,,T,,M,,N,,K,N*2C",
    "\$GPDTM,,,,,,,,*4A",
    "\$GPRMC,,V,,,,,,,,,,N,V*29",
    "\$GPGNS,,,,,,N,,,,,,,V*79",
    "\$GPGGA,,,,,,0,,,,,,,,*66",
    "\$GPGSV,3,1,09,03,29,281,,04,18,319,,16,60,239,,26,73,355,,1*60",
    "\$GPGSV,3,2,09,27,18,180,,28,39,060,,29,15,049,,31,55,010,,1*6E",
    "\$GPGSV,3,3,09,32,29,137,,1*52",
    "\$GLGSV,2,1,08,14,43,011,,16,07,248,,15,46,288,,05,38,326,,1*72",
    "\$GPGGA,,,,,,0,,,,,,,,*66",
    "\$GPGNS,,,,,,N,,,,,,,V*79",
    "\$GQGSV,1,1,03,02,64,078,,03,12,142,,04,44,162,,1*5F",
)

val HUNDRED = BigDecimal(100)
val SIXTY = BigDecimal(60)

fun main() {
    data.forEach {
        val nmea = NMEA.valueOf(it)
        if (nmea.toNmeaString() != it) {
            println("Your value:     ${nmea.toNmeaString()}")
            println("Original value: $it")
            throw IllegalArgumentException("NMEA value is not equal to original value: $it")
        }
    }
    println("All tests passed")


    val lat = 28.139908
    val lon = 113.8939
    val degree = lat.toInt()
    val minute = (lat - degree) * 60
    val latitude = degree + minute / 100

    val degree2 = lon.toInt()
    val minute2 = (lon - degree2) * 60
    val longitude = degree2 + minute2 / 100

    println("Latitude: $latitude")
    println("Longitude: $longitude")
}