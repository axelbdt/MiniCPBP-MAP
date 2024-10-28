# uploads the scripts to the server
# run from BPutils

scp \
  sbatch_scripts/job.sh \
  sbatch_scripts/job_bulk.sh \
  sbatch_scripts/schedule.sh \
  sbatch_scripts/schedule_bulk.sh \
  sbatch_scripts/job_solve.sh \
  axelbdt@narval.alliancecan.ca:projects/def-pesantg/axelbdt/
