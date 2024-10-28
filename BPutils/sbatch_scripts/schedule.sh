#!/bin/bash

# Define the ranges for holes and models
nbHoles_array=(350 400 500 600 700)
objective_array=(diagonal pseudodiagonal_30_6_0 pseudodiagonal_30_6_6)
searchType_array=(lds)
modelNumber_array=(0 1 2 3 4)
truncateRate_array=(0) # (0 50)

# Iterate over holes and models
for nbHoles in "${nbHoles_array[@]}"; do
    for objective in "${objective_array[@]}"; do
        for searchType in "${searchType_array[@]}"; do
            for modelNumber in "${modelNumber_array[@]}"; do
	            for truncateRate in "${truncateRate_array[@]}"; do
                        # Construct the sbatch command
                        sbatch_command="sbatch job.sh --nbHoles $nbHoles --objective $objective --searchType $searchType --modelNumber $modelNumber --truncateRate $truncateRate"
                        
                        # Execute the sbatch command
                        eval "$sbatch_command"
                        
                        # Print the executed command for logging purposes
                        echo "Executing: $sbatch_command"

	                # 2-second sleep to not overload the scheduler
	                sleep 2
	            done
            done
        done
    done
done

