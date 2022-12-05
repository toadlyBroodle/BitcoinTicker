package org.bitanon.bitcointicker

class Calculator {
	companion object {

		fun getDeltas(chart: List<List<Number>>): MutableList<Float> {
			val deltas = mutableListOf(0f, 0f, 0f) //daily, weekly, monthly changes

			val vals = getValues(chart)
			val lstInd = vals.lastIndex
			val averages = getAverages(chart)
			// calculate daily percent delta
			deltas[0] = getPercDelta(vals[lstInd], vals[lstInd - 1])
			// calculate percent delta from weekly SMA
			deltas[1] = getPercDelta(vals[lstInd], averages[0])
			// calculate percent delta from monthly SMA
			deltas[2] = getPercDelta(vals[lstInd], averages[1])

			return deltas
		}

		fun getAverages(chart: List<List<Number>>): MutableList<Float> {
			val avgs = mutableListOf(0f, 0f) //weekly, monthly

			val vals = getValues(chart)
			// calculate weekly price avg
			for (i in (vals.lastIndex - 6)..vals.lastIndex)
				avgs[0] += vals[i]
			avgs[0] = avgs[0]/7 //days

			// calculate monthly price avg
			for (i in 0..vals.lastIndex) avgs[1] += vals[i]
			avgs[1] = avgs[1]/30 //days
			//println("calculated price avgs: weekly=${avgs[0]}, monthly=${avgs[1]}")
			return avgs
		}

		fun getValues(chart: List<List<Number>>): List<Float> {
			val vals = mutableListOf<Float>()
			for (day in chart) {
				vals.add(day[1].toFloat())
			}
			return vals
		}

		fun getPercDelta(new: Float, orig: Float): Float {
			return ((new - orig) / orig) * 100
		}
	}
}