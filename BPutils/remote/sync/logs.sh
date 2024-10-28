# download logs from the server
# run from BPutils

rm -rf older_logs
mv old_logs older_logs
mv logs old_logs
scp -r axelbdt@narval.alliancecan.ca:projects/def-pesantg/axelbdt/logs .





