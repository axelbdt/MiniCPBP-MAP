#!/bin/bash
#SBATCH --time=4:00:00
#SBATCH --account=def-pesantg
#SBATCH --mem=2G
#SBATCH --array=1-10

# Default values
nbHoles=""
searchType=""
modelNumber=""
truncateRate=""

# Parse command line arguments
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
        --modelNumber)
            modelNumber="$2"
            shift 2
            ;;
        --truncateRate)
            truncateRate="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Check if required arguments are provided
if [ -z "$nbHoles" ] || [ -z "$objective"] || [ -z "$searchType" ] || [ -z "$modelNumber" ] || [ -z "$truncateRate" ]; then
    echo "Usage: $0 --nbHoles <value> --objective <value> --searchType <value> --modelNumber <value> --truncateRate <value>"
    exit 1
fi

cd /home/axelbdt/projects/def-pesantg/axelbdt
module load java/21
java -jar latin-square.jar --n=30 --nbHoles=$nbHoles --nbFile=$SLURM_ARRAY_TASK_ID --searchType=$searchType --modelNumber=$modelNumber --truncateRate=$truncateRate
