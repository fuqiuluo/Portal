//
// Created by fuqiuluo on 2024/10/15.
//

#ifndef PORTAL_SENSOR_HOOK_H
#define PORTAL_SENSOR_HOOK_H

#include "android/sensor.h"

// ssize_t SensorEventQueue::write(const sp<BitTube>& tube,
//        ASensorEvent const* events, size_t numEvents)
typedef int64_t (*OriginalSensorEventQueueWriteType)(void*, void*, int64_t);

// void convertToSensorEvent(const Event &src, sensors_event_t *dst);
typedef void (*ConvertToSensorEventType)(void*, sensor_event_t*);

void doSensorHook();

#endif //PORTAL_SENSOR_HOOK_H
