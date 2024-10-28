#!/bin/bash
#SBATCH --time=12:00:00
#SBATCH --account=def-pesantg
#SBATCH --mem=2G
#SBATCH --array=1-10

cd /home/axelbdt/projects/def-pesantg/axelbdt
module load java/21
java -jar latin-square.jar --n=30 --nbHoles=500 --nbFile=$SLURM_ARRAY_TASK_ID --searchType=dfs --modelNumber=2 --truncateRate=0
