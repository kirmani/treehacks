#! /usr/bin/env python
# -*- coding: utf-8 -*-
# vim:fenc=utf-8
#
# Copyright © 2015 Sean Kirmani <sean@kirmani.io>
#
# Distributed under terms of the MIT license.

import json
import threading
import time
import os

from flask import Flask
from flask import jsonify
from flask import request

BASE_DIR = os.path.dirname(os.path.realpath(__file__))
DATA_DIR = BASE_DIR + "/data"
ADF_FILE_NAME = "adf"
TIMEOUT_SEC = 15

app = Flask(__name__, static_url_path='')
app.secret_key = 'treehacks'

sessions = {}
lock = threading.Lock()

@app.route('/')
def Home():
  return 'Hello world!'

@app.route('/session/<session_id>/upload', methods=['POST'])
def file_upload(session_id):
  if session_id not in sessions:
    return jsonify({"error": "Session does not exist."})
  adf = request.files['adf']
  session_dir = DATA_DIR + "/" + session_id
  if not os.path.isdir(session_dir):
    os.mkdir(session_dir)
  adf_file = session_dir + "/" + ADF_FILE_NAME
  lock.acquire()
  adf.save(adf_file)
  lock.release()
  sessions[session_id]['join_waiting'] = False
  sessions[session_id]['adf_last_update'] = time.time()
  return jsonify(sessions[session_id])

@app.route('/session/<session_id>/download', methods=['GET'])
def SessionDownload(session_id):
  if session_id not in sessions:
    return jsonify({"error": "Session does not exist."})
  join_request_time = sessions[session_id]['join_request_time']
  last_update = sessions[session_id]['adf_last_update']
  if last_update < join_request_time:
    return jsonify({"download_ready": False})
  session_adf_file = DATA_DIR + "/" + session_id + "/" + ADF_FILE_NAME
  if not os.path.isfile(session_adf_file):
    return jsonify({"error": "No ADF exists."})
  headers = {"Content-Disposition": "attachment; filename=%s_adf" % session_id}
  with open(session_adf_file, 'rb') as f:
    lock.acquire()
    body = f.read()
    lock.release()
    return jsonify({"download_ready": True, "data": body})
  return jsonify({"error": "Unexpected error."})

@app.route('/session/<session_id>/join', methods=['POST'])
def SessionJoin(session_id):
  if session_id not in sessions:
    return jsonify({"error": "Session does not exist."})
  sessions[session_id]['join_waiting'] = True
  sessions[session_id]['join_request_time'] = time.time()
  if len(sessions[session_id]['devices']) == 0:
    sessions[session_id]['join_waiting'] = False
  return jsonify(sessions[session_id])

@app.route('/debug/sessions', methods=['GET'])
def DebugSessions():
  return jsonify(sessions)

@app.route('/session/<session_id>', methods=['POST', 'PUT', 'GET'])
def Session(session_id):
  if request.method == 'POST':
    if session_id in sessions:
      return jsonify({"error": "Session already exists."})
    print("Creating session: " + session_id)
    session_data = {
          'join_waiting': False,
          'devices': {},
          'adf_last_update': time.time(),
        }
    sessions[session_id] = session_data
    return jsonify(session_data)
  if request.method == 'PUT':
    if session_id not in sessions:
      return jsonify({"error": "Session does not exist."})
    devices = request.json['devices']
    print(devices)
    for uuid in devices:
      sessions[session_id]['devices'][uuid] = devices[uuid]
      if len(sessions[session_id]['devices']) == 1:
        sessions[session_id]['devices'][uuid]['host'] = True
      sessions[session_id]['devices'][uuid]['last_update'] = time.time()
    return jsonify(sessions[session_id])
  if request.method == 'GET':
    if session_id not in sessions:
      return jsonify({"error": "Session does not exist."})
    current_time = time.time()
    devices_to_delete = []
    for device in sessions[session_id]['devices']:
      if current_time - sessions[session_id]['devices'][device]['last_update'] > TIMEOUT_SEC:
        devices_to_delete.append(device)
    for device in devices_to_delete:
      del sessions[session_id]['devices'][device]
      if len(sessions[session_id]['devices']) == 0:
        sessions[session_id]['join_waiting'] = False
    return jsonify(sessions[session_id])
  return jsonify({"error": "Unexpected error."})

if __name__ == '__main__':
  port = int(os.environ.get('PORT', 33507))
  app.run(host='0.0.0.0', port=port, debug=True)
