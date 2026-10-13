/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#include <iostream>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <string.h>

#include <cutils/properties.h>

#define VERSION_STRING "1.0.1"

// Device control definitions
#define SET_CUR_VALUE 0
#define TOUCH_PEN_MODE 20
#define TOUCH_MAGIC 't'
#define TOUCH_IOC_SETMODE _IO(TOUCH_MAGIC, SET_CUR_VALUE)
#define TOUCH_DEV_PATH "/dev/xiaomi-touch"

int main(int argc, char **argv) {
    int value = 18; // Default to 18 (enabled)

    // Parse the input value
    if (argc == 2) {
        value = atoi(argv[1]);
    } else {
        char prop_val[PROPERTY_VALUE_MAX];
        property_get("persist.vendor.parts.pen", prop_val, "18");
        value = atoi(prop_val);
    }

    // Open the touch device
    int fd = open(TOUCH_DEV_PATH, O_RDWR);
    if (fd < 0) {
        fprintf(stderr, "Error opening device %s: %s\n", 
                TOUCH_DEV_PATH, strerror(errno));
        return -1;
    }

    if (value < 0 || value > 20) {
        fprintf(stderr, "Warning: Value %d outside normal range (0-20)\n", value);
    }
    fprintf(stdout, "Setting pen mode to: %d\n", value);
    
    // Prepare and send the command
    int arg[256] = {0};
    arg[0] = TOUCH_PEN_MODE;
    arg[1] = value;
    int result = ioctl(fd, TOUCH_IOC_SETMODE, &arg);
    
    // Check for errors
    if (result < 0) {
        fprintf(stderr, "Error setting pen mode: %s\n", strerror(errno));
        close(fd);
        return -1;
    }

    // Clean up
    close(fd);
    fprintf(stdout, "Pen mode set successfully\n");
    return 0;
}
