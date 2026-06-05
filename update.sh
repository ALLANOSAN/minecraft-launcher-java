#!/bin/bash
# Script para atualizar o código e recompilar
echo "Puxando atualizações..."
git pull
echo "Recompilando..."
./build.sh package
echo "Atualização finalizada."
