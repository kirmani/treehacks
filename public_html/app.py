#! /usr/bin/env python
# -*- coding: utf-8 -*-
# vim:fenc=utf-8
#
# Copyright © 2015 Sean Kirmani <sean@kirmani.io>
#
# Distributed under terms of the MIT license.

import json
import os

from flask import Flask

app = Flask(__name__, static_url_path='')
app.secret_key = 'multi_tango_kirmani_io'

@app.route('/')
def Home():
  return 'Hello world!'

def _LoadFile(filename):
  try:
    with open(filename, 'r+') as f:
      return json.load(fp=f)
  except IOError:
    return {}

def _WriteFile(data, filename):
  j = json.dumps(data, indent=4)
  with open(filename, 'w') as f:
    f.write(j)

if __name__ == '__main__':
  port = int(os.environ.get('PORT', 33507))
  app.run(host='0.0.0.0', port=port, debug=True)
