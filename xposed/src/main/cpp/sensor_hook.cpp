//
// Created by fuqiuluo on 2024/10/15.
//
#include <dobby.h>
#include <unistd.h>
#include "sensor_hook.h"
#include "logging.h"
#include "elf_util.h"
#include "dobby_hook.h"

#define LIBSF_PATH "/system/lib64/libsensorservice.so"

// _ZN7android16SensorEventQueue5writeERKNS_2spINS_7BitTubeEEEPK12ASensorEventm
OriginalSensorEventQueueWriteType OriginalSensorEventQueueWrite = nullptr;

int64_t SensorEventQueueWrite(void *tube, void *events, int64_t numEvents) {
    LOGD("SensorEventQueueWrite called");
    return OriginalSensorEventQueueWrite(tube, events, numEvents);
}

void doSensorHook() {
    SandHook::ElfImg sensorService("/system/lib64/libsensorservice.so");

    if (!sensorService.isValid()) {
        LOGE("failed to load libsensorservice");
        return;
    }

    auto sensorWrite = sensorService.getSymbolAddress<void*>("_ZN7android16SensorEventQueue5writeERKNS_2spINS_7BitTubeEEEPK12ASensorEventm");
    if (sensorWrite == nullptr) {
        sensorWrite = sensorService.getSymbolAddress<void*>("_ZN7android16SensorEventQueue5writeERKNS_2spINS_7BitTubeEEEPK12ASensorEventj");
    }

    auto convertToSensorEvent = sensorService.getSymbolAddress<void*>("_ZN7android8hardware7sensors4V1_014implementation20convertToSensorEventERKNS2_5EventEP15sensors_event_t");

    LOGD("Dobby SensorEventQueue::write found at %p", sensorWrite);
    LOGD("Dobby convertToSensorEvent found at %p", convertToSensorEvent);


    //OriginalSensorEventQueueWrite = (OriginalSensorEventQueueWriteType)InlineHooker(sensorWrite, (void *)SensorEventQueueWrite);
}

