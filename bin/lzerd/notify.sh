#!/bin/bash

echo "The LZerD job you started is complete!\nThe ticket id is $1" | mail -s "LZerD job complete!" waldena@purdue.edu
