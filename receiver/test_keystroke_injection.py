#!/usr/bin/env python3
"""
Test script for verifying simulated Windows SendInput virtual keys.
"""
import sys

VK_UP = 0x26
VK_DOWN = 0x28
VK_LEFT = 0x25
VK_RIGHT = 0x27
VK_PRIOR = 0x21
VK_NEXT = 0x22

def check_vk_constants():
    assert VK_UP == 38
    assert VK_DOWN == 40
    assert VK_LEFT == 37
    assert VK_RIGHT == 39
    assert VK_PRIOR == 33
    assert VK_NEXT == 34
    print("Virtual key constants verified.")

if __name__ == "__main__":
    check_vk_constants()
