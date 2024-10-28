#!/bin/bash
#SBATCH --time=10:00:00
#SBATCH --account=def-pesantg
#SBATCH --mem=2G
#SBATCH --array=1-10

nbHoles=""
objective=""
searchType=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --nbHoles)
            nbHoles="$2"
            shift 2
            ;;
        --objective)
            objective="$2"
            shift 2
            ;;
        --searchType)
            searchType="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

cd /home/axelbdt/projects/def-pesantg/axelbdt
module load java/21
java -jar latin-square.jar --nbHoles=$nbHoles --nbFile=$SLURM_ARRAY_TASK_ID --objective=$objective --searchType=$searchType
