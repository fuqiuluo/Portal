package moe.microbios.nmea

data class NMEA(
    val talkerId: String,
    val value: NmeaValue
) {
    companion object {
        fun calculateNmeaChecksum(nmeaSentence: String): String {
            val sentence = nmeaSentence.trimStart('$').substringBefore('*')
            var checksum = 0
            for (char in sentence) {
                checksum = checksum xor char.code
            }
            return String.format("%02X", checksum)
        }

        fun valueOf(value: String): NMEA {
            if (!value.startsWith("$")) {
                throw IllegalArgumentException("the statement doesn't start with '\$'")
            }

            val sentence = value.trimStart('$').substringBefore('*')
            val sequences = sentence.split(",")

            val (talkerId, statementId) = sequences[0].let {
                it.substring(0, 2) to it.substring(2)
            }

            when (statementId) {
                "DTM" -> {
                    val localCoordinateSystemCode = sequences[1]
                    val coordinateSystemSubCode = sequences[2]
                    val latitudeOffset = sequences[3].toDoubleOrNull()
                    val latitudeHemisphere = sequences[4]
                    val longitudeOffset = sequences[5].toDoubleOrNull()
                    val longitudeHemisphere = sequences[6]
                    val heightOffset = sequences[7].toDoubleOrNull()
                    val coordinateSystemCode = sequences[8]
                    return NMEA(talkerId, NmeaValue.DTM(
                        localCoordinateSystemCode = localCoordinateSystemCode,
                        coordinateSystemSubCode = coordinateSystemSubCode,
                        latitudeOffset = latitudeOffset,
                        latitudeHemisphere = latitudeHemisphere,
                        longitudeOffset = longitudeOffset,
                        longitudeHemisphere = longitudeHemisphere,
                        heightOffset = heightOffset,
                        coordinateSystemCode = coordinateSystemCode
                    ))
                }
                "GGA" -> {
                    val utc = sequences[1]
                    val latitude = sequences[2].toDoubleOrNull()
                    val latitudeHemisphere = sequences[3]
                    val longitude = sequences[4].toDoubleOrNull()
                    val longitudeHemisphere = sequences[5]
                    val fixQuality = sequences[6].toInt()
                    val satellites = sequences[7].toIntOrNull()
                    val hdop = sequences[8].toDoubleOrNull()
                    val antennaAltitude = sequences[9].toDoubleOrNull()
                    val antennaAltitudeUnit = sequences[10]
                    val geoidalSeparation = sequences[11].toDoubleOrNull()
                    val geoidalSeparationUnit = sequences[12]
                    val dgpsAge = sequences[13].toDoubleOrNull()
                    val dgpsStationId = sequences[14]
                    return NMEA(talkerId, NmeaValue.GGA(
                        utc = utc,
                        latitude = latitude,
                        latitudeHemisphere = latitudeHemisphere,
                        longitude = longitude,
                        longitudeHemisphere = longitudeHemisphere,
                        fixQuality = fixQuality,
                        satellites = satellites,
                        hdop = hdop,
                        antennaAltitude = antennaAltitude,
                        antennaAltitudeUnit = antennaAltitudeUnit,
                        geoidalSeparation = geoidalSeparation,
                        geoidalSeparationUnit = geoidalSeparationUnit,
                        dgpsAge = dgpsAge,
                        dgpsStationId = dgpsStationId
                    ))
                }
                "GNS" -> {
                    val utc = sequences[1]
                    val latitude = sequences[2].toDoubleOrNull()
                    val latitudeHemisphere = sequences[3]
                    val longitude = sequences[4].toDoubleOrNull()
                    val longitudeHemisphere = sequences[5]
                    val mode = sequences[6]
                    val satellites = sequences[7].toIntOrNull()
                    val hdop = sequences[8].toDoubleOrNull()
                    val altitude = sequences[9].toDoubleOrNull()
                    val geoidHeight = sequences[10].toDoubleOrNull()
                    val ageOfDifferentialData = sequences[11].toDoubleOrNull()
                    val differentialStationId = sequences[12]
                    val navStatus = if (sequences.size > 13) {
                        sequences[13]
                    } else "V"
                    return NMEA(talkerId, NmeaValue.GNS(
                        utc = utc,
                        latitude = latitude,
                        latitudeHemisphere = latitudeHemisphere,
                        longitude = longitude,
                        longitudeHemisphere = longitudeHemisphere,
                        mode = mode,
                        satellites = satellites,
                        hdop = hdop,
                        altitude = altitude,
                        geoidHeight = geoidHeight,
                        ageOfDifferentialData = ageOfDifferentialData,
                        differentialStationId = differentialStationId,
                        navStatus = navStatus
                    ))
                }
                "GSA" -> {
                    val mode = sequences[1]
                    val fixStatus = sequences[2].toInt()
                    val prn = sequences.subList(3, 15).map { it.toIntOrNull() }
                    val pdop = sequences[15].toDoubleOrNull()
                    val hdop = sequences[16].toDoubleOrNull()
                    val vdop = sequences[17].toDoubleOrNull()
                    val systemId = if (sequences.size > 18) {
                        sequences[18]
                    } else null
                    return NMEA(talkerId, NmeaValue.GSA(mode, fixStatus, prn, pdop, hdop, vdop, systemId))
                }
                "GSV" -> {
                    val totalMessages = sequences[1].toInt()
                    val messageNumber = sequences[2].toInt()
                    val totalSatellitesInView = sequences[3].toInt()
                    val satellites = sequences.subList(4, sequences.size - 1).chunked(4).map {
                        NmeaValue.GSV.Satellite(
                            prn = it[0].toInt(),
                            elevation = it[1].toInt(),
                            azimuth = it[2].toInt(),
                            snr = it[3].toIntOrNull()
                        )
                    }
                    val infoId = if((sequences.size - 4) % 4 != 0) {
                        sequences.last()
                    } else "0"
                    return NMEA(talkerId, NmeaValue.GSV(totalMessages, messageNumber, totalSatellitesInView, satellites, infoId))
                }
                "RMC" -> {
                    val utc = sequences[1]
                    val status = sequences[2]
                    val latitude = sequences[3].toDoubleOrNull()
                    val latitudeHemisphere = sequences[4]
                    val longitude = sequences[5].toDoubleOrNull()
                    val longitudeHemisphere = sequences[6]
                    val speedKnots = sequences[7].toDoubleOrNull()
                    val trackAngle = sequences[8].toDoubleOrNull()
                    val date = sequences[9]
                    val magneticVariation = sequences[10].toDoubleOrNull()
                    val magneticVariationDirection = sequences[11]
                    val mode = if (sequences.size > 12) {
                        sequences[12]
                    } else ""
                    val navStatus = if (sequences.size > 13) {
                        sequences[13]
                    } else "V"
                    return NMEA(talkerId, NmeaValue.RMC(
                        utc = utc,
                        status = status,
                        latitude = latitude,
                        latitudeHemisphere = latitudeHemisphere,
                        longitude = longitude,
                        longitudeHemisphere = longitudeHemisphere,
                        speedKnots = speedKnots,
                        trackAngle = trackAngle,
                        date = date,
                        magneticVariation = magneticVariation,
                        magneticVariationDirection = magneticVariationDirection,
                        mode = mode,
                        navStatus = navStatus
                    ))
                }
                "VTG" -> {
                    val trueTrack = sequences[1].toDoubleOrNull()
                    val trueTrackMode = sequences[2]
                    val magneticTrack = sequences[3].toDoubleOrNull()
                    val magneticTrackMode = sequences[4]
                    val groundSpeedKnots = sequences[5].toDoubleOrNull()
                    val groundSpeedUnit = sequences[6]
                    val groundSpeedKph = sequences[7].toDoubleOrNull()
                    val groundSpeedKphUnit = sequences[8]
                    val mode = sequences[9]
                    return NMEA(talkerId, NmeaValue.VTG(
                        trueTrack = trueTrack,
                        trueTrackMode = trueTrackMode,
                        magneticTrack = magneticTrack,
                        magneticTrackMode = magneticTrackMode,
                        groundSpeedKnots = groundSpeedKnots,
                        groundSpeedUnit = groundSpeedUnit,
                        groundSpeedKph = groundSpeedKph,
                        groundSpeedKphUnit = groundSpeedKphUnit,
                        mode = mode
                    ))
                }
                else -> {
                    throw IllegalArgumentException("Unsupported statementId: $statementId")
                }
            }
        }
    }

    fun toNmeaString(): String {
        val data = value.toNmeaString()
        val sentence = "\$$talkerId$data*"
        val checksum = calculateNmeaChecksum(sentence)
        return "$sentence$checksum"
    }
}
